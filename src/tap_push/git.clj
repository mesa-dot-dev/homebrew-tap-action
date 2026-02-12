(ns tap-push.git
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [tap-push.gh :as gh]))

(defn clone-tap
  "Clones the tap repository to a temporary directory.
   Returns the path to the cloned directory."
  [{:keys [tap token branch]}]
  (let [dir (str (fs/create-temp-dir {:prefix "tap-push-"}))]
    (p/sh "git" "clone" "--depth" "1" "--branch" (or branch "main")
           (str "https://x-access-token:" token "@github.com/" tap ".git")
           dir)
    dir))

(defn push-to-tap
  "Commits and pushes changes in the tap directory.
   Returns true if changes were pushed, false if no changes."
  [{:keys [tap-dir tap branch committer-name committer-email commit-message version]}]
  (let [msg (str/replace (or commit-message "Deploy Formula ${VERSION}") "${VERSION}" version)
        branch (or branch "main")]
    (p/sh {:dir tap-dir} "git" "config" "user.name" (or committer-name "github-actions[bot]"))
    (p/sh {:dir tap-dir} "git" "config" "user.email" (or committer-email "github-actions[bot]@users.noreply.github.com"))
    (p/sh {:dir tap-dir} "git" "add" "-A")
    (let [diff (p/sh {:dir tap-dir :continue true} "git" "diff" "--cached" "--quiet")]
      (if (zero? (:exit diff))
        (do (gh/notice "No changes to commit")
            false)
        (do (p/sh {:dir tap-dir} "git" "-c" "commit.gpgsign=false" "commit" "-m" msg)
            (p/sh {:dir tap-dir} "git" "push" "origin" branch)
            (gh/notice (str "Pushed formulae to " tap "@" branch))
            true)))))
