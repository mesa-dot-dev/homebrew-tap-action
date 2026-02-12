(ns tap-push.core
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [tap-push.gh :as gh]
    [tap-push.git :as git]
    [tap-push.resolve :as resolve]
    [tap-push.template :as template]))


(defn- env
  "Gets environment variable value, returns nil if blank."
  [k]
  (let [v (System/getenv k)]
    (when-not (str/blank? v) v)))


(defn- require!
  "Returns value if truthy, otherwise prints error and exits."
  [value error-msg]
  (when-not value
    (gh/error error-msg)
    (System/exit 1))
  value)


(defn- require-ok!
  "Extracts :ok value from result map, or prints :error and exits."
  [{:keys [ok error]}]
  (when error
    (gh/error error)
    (System/exit 1))
  ok)


(defn -main
  [& _args]
  ;; Mask the tap token
  (when-let [token (env "INPUT_TAP_TOKEN")]
    (gh/add-mask token))

  (let [;; Resolve basic inputs
        name (require! (resolve/resolve-name
                         {:input-name (env "INPUT_NAME")
                          :github-repository (env "GITHUB_REPOSITORY")})
                       "Could not resolve name. Provide the 'name' input.")
        _ (gh/notice (str "Resolved NAME: " name))

        version (require! (resolve/resolve-version
                            {:input-version (env "INPUT_VERSION")
                             :github-ref-name (env "GITHUB_REF_NAME")})
                          "Could not resolve version. Provide 'version' input or run from a tag.")
        _ (gh/notice (str "Resolved VERSION: " version))

        ;; Read and analyze template
        workspace (require! (env "GITHUB_WORKSPACE") "GITHUB_WORKSPACE is not set.")
        input-template (require! (env "INPUT_TEMPLATE") "The 'template' input is required.")
        template-path (str workspace "/" input-template)
        _ (when-not (fs/exists? template-path)
            (gh/error (str "Template file not found: " input-template))
            (System/exit 1))

        template-content (slurp template-path)
        required-vars (template/detect-required-vars template-content)
        _ (gh/notice (str "Template requires: " (str/join " " (sort required-vars))))

        ;; Resolve URL and SHA256 unconditionally — SHA256 is always
        ;; exposed as an action output, and computing it requires a URL.
        ;; Only fail if the template requires them and resolution fails.
        url-result (resolve/resolve-url
                     {:input-url (env "INPUT_URL")
                      :github-repository (env "GITHUB_REPOSITORY")
                      :version version})
        _ (when (and (required-vars "URL") (:error url-result))
            (gh/error (:error url-result))
            (System/exit 1))
        url (:ok url-result)
        _ (when url (gh/notice (str "Resolved URL: " url)))

        sha256-result (resolve/resolve-sha256
                        {:input-sha256 (env "INPUT_SHA256")
                         :url url})
        _ (when (and (required-vars "SHA256") (:error sha256-result))
            (gh/error (:error sha256-result))
            (System/exit 1))
        sha256 (:ok sha256-result)
        _ (when sha256 (gh/notice (str "Resolved SHA256: " sha256)))

        license (when (required-vars "LICENSE")
                  (let [l (require-ok! (resolve/resolve-license
                                         {:github-repository (env "GITHUB_REPOSITORY")}))]
                    (gh/notice (str "Resolved LICENSE: " l))
                    l))

        latest-class (when (required-vars "FORMULA_CLASS_NAME")
                       (let [c (resolve/class-s name)]
                         (gh/notice (str "Latest FORMULA_CLASS_NAME: " c))
                         c))

        versioned-class (when (and (required-vars "FORMULA_CLASS_NAME")
                                   (env "INPUT_VERSIONED_PATH"))
                          (let [c (resolve/class-s (str name "@" version))]
                            (gh/notice (str "Versioned FORMULA_CLASS_NAME: " c))
                            c))

        repo-url (when (required-vars "REPO_URL")
                   (let [r (require! (resolve/resolve-repo-url
                                       {:github-repository (env "GITHUB_REPOSITORY")})
                                     "Could not resolve REPO_URL. GITHUB_REPOSITORY is not set.")]
                     (gh/notice (str "Resolved REPO_URL: " r))
                     r))

        ;; Build vars map for latest formula
        vars {"VERSION" version
              "NAME" name
              "URL" (or url "")
              "SHA256" (or sha256 "")
              "LICENSE" (or license "")
              "FORMULA_CLASS_NAME" (or latest-class "")
              "REPO_URL" (or repo-url "")}]

    ;; Validate all required vars are resolved
    (when-let [missing (template/validate-vars required-vars vars)]
      (doseq [var-name missing]
        (gh/error (str "Template requires $" var-name " but it could not be resolved.")))
      (System/exit 1))

    ;; Set GitHub outputs
    (gh/set-output "version" version)
    (gh/set-output "sha256" (or sha256 ""))

    ;; Clone tap
    (let [tap-dir (git/clone-tap {:tap (env "INPUT_TAP")
                                  :token (env "INPUT_TAP_TOKEN")
                                  :branch (env "INPUT_TAP_BRANCH")})]

      ;; Generate latest formula
      (let [latest-path (str tap-dir "/" (env "INPUT_LATEST_PATH"))]
        (template/generate-formula template-path latest-path vars)
        (gh/notice (str "Generated latest formula: " (env "INPUT_LATEST_PATH"))))

      ;; Generate versioned formula (optional)
      (when-let [versioned-path-template (env "INPUT_VERSIONED_PATH")]
        (let [resolved-path (str/replace versioned-path-template "${VERSION}" version)
              versioned-full (str tap-dir "/" resolved-path)
              versioned-vars (assoc vars "FORMULA_CLASS_NAME" (or versioned-class latest-class ""))]
          (template/generate-formula template-path versioned-full versioned-vars)
          (gh/notice (str "Generated versioned formula: " resolved-path))))

      ;; Commit and push
      (git/push-to-tap {:tap-dir tap-dir
                        :tap (env "INPUT_TAP")
                        :branch (env "INPUT_TAP_BRANCH")
                        :committer-name (env "INPUT_COMMITTER_NAME")
                        :committer-email (env "INPUT_COMMITTER_EMAIL")
                        :commit-message (env "INPUT_COMMIT_MESSAGE")
                        :version version})

      ;; Cleanup
      (fs/delete-tree tap-dir))))
