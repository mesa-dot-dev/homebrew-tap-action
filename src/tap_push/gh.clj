(ns tap-push.gh)


(defn notice
  [msg]
  (println (str "::notice::" msg)))


(defn error
  [msg]
  (binding [*out* *err*]
    (println (str "::error::" msg))))


(defn add-mask
  [value]
  (println (str "::add-mask::" value)))


(defn set-output
  [k v]
  (if-let [output-file (System/getenv "GITHUB_OUTPUT")]
    (spit output-file (str k "=" v "\n") :append true)
    (binding [*out* *err*]
      (println (str "::warning::GITHUB_OUTPUT not set, cannot write output " k "=" v)))))
