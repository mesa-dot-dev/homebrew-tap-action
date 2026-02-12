(ns tap-push.resolve
  (:require
    [babashka.fs :as fs]
    [babashka.process :as p]
    [clojure.string :as str]))


(defn resolve-name
  "Resolves project name from explicit input or GITHUB_REPOSITORY.
   Returns the name string, or nil if unresolvable."
  [{:keys [input-name github-repository]}]
  (if (not (str/blank? input-name))
    input-name
    (when (not (str/blank? github-repository))
      (second (str/split github-repository #"/")))))


(defn resolve-version
  "Resolves version from explicit input or git ref name (strips v prefix).
   Returns the version string, or nil if unresolvable."
  [{:keys [input-version github-ref-name]}]
  (cond
    (not (str/blank? input-version)) input-version
    (not (str/blank? github-ref-name)) (str/replace-first github-ref-name #"^v" "")
    :else nil))


(defn class-s
  "Computes the Homebrew formula class name from a formula name.
   Matches Homebrew's Formulary.class_s Ruby implementation.
   Examples: \"git-fs\" => \"GitFs\", \"git-fs@1.2.3\" => \"GitFsAT123\""
  [name]
  (-> name
      str/capitalize
      (str/replace #"[-_.\s](\w)" (fn [[_ c]] (str/upper-case c)))
      (str/replace "+" "x")
      (str/replace #"@(\d)" "AT$1")))


(defn resolve-url
  "Resolves artifact URL from explicit input or GitHub release auto-discovery.
   Returns {:ok url} or {:error msg}."
  [{:keys [input-url github-repository version]}]
  (if (not (str/blank? input-url))
    {:ok input-url}
    (let [tag (str "v" version)
          result (p/sh {:continue true :err :string}
                       "gh" "release" "view" tag
                       "--json" "assets" "--jq" ".assets[].name")
          assets (when (zero? (:exit result))
                   (str/trim (:out result)))]
      (if (str/blank? assets)
        {:error (str "Could not auto-discover URL: no release found for tag '" tag "'. Provide the 'url' input.")}
        (let [lines (str/split-lines assets)
              macos (filter #(re-find #"(?i)(macos|darwin)" %) lines)
              universal (first (filter #(re-find #"(?i)universal" %) macos))
              asset (or universal (first macos))]
          (if asset
            {:ok (str "https://github.com/" github-repository "/releases/download/" tag "/" asset)}
            {:error (str "Could not auto-discover URL: no macOS asset found in release '" tag "'. Provide the 'url' input.")}))))))


(defn resolve-sha256
  "Resolves SHA256 from explicit input or by downloading and computing.
   Returns {:ok sha256} or {:error msg}."
  [{:keys [input-sha256 url]}]
  (if (not (str/blank? input-sha256))
    {:ok input-sha256}
    (if (str/blank? url)
      {:error "Cannot compute SHA256: no URL available. Provide the 'sha256' or 'url' input."}
      (let [tmpfile (str (System/getProperty "java.io.tmpdir") "/artifact-" (random-uuid) ".tmp")
            dl (p/sh {:continue true :err :string} "curl" "-fSL" "-o" tmpfile url)]
        (if (not (zero? (:exit dl)))
          (do (fs/delete-if-exists tmpfile)
              {:error (str "Failed to download artifact from: " url)})
          (let [sha-result (try (p/sh "sha256sum" tmpfile)
                                (catch Exception _
                                  (p/sh "shasum" "-a" "256" tmpfile)))
                sha (-> (:out sha-result) str/trim (str/split #"\s+") first)]
            (fs/delete-if-exists tmpfile)
            {:ok sha}))))))


(defn resolve-license
  "Resolves license SPDX ID via GitHub API.
   Returns {:ok license} or {:error msg}."
  [{:keys [github-repository]}]
  (let [result (p/sh {:continue true :err :string}
                     "gh" "api" (str "repos/" github-repository)
                     "--jq" ".license.spdx_id")
        license (when (zero? (:exit result))
                  (str/trim (:out result)))]
    (if (or (str/blank? license) (= license "null") (= license "NOASSERTION"))
      {:error "Could not detect repository license. Template requires $LICENSE but no SPDX license found."}
      {:ok license})))
