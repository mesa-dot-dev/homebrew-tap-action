(ns tap-push.template
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def known-vars
  ["VERSION" "NAME" "URL" "SHA256" "LICENSE" "FORMULA_CLASS_NAME"])

(defn detect-required-vars
  "Scans template content for known variable references (${VAR} or $VAR syntax).
   Returns a set of variable name strings found."
  [template-content]
  (into #{}
        (filter (fn [var-name]
                  (re-find (re-pattern (str "\\$\\{" var-name "\\}|\\$" var-name "(?![A-Za-z0-9_])"))
                           template-content)))
        known-vars))

(defn validate-vars
  "Validates that all required variables have non-empty values.
   required is a set of var-name strings, vars is a map of var-name -> value.
   Returns nil if valid, or a vector of missing var names."
  [required vars]
  (let [missing (filterv (fn [var-name]
                           (str/blank? (get vars var-name)))
                         required)]
    (when (seq missing)
      missing)))

(defn substitute-vars
  "Replaces ${VAR} placeholders in template with values from vars map.
   Only replaces the ${VAR} form (with braces)."
  [template vars]
  (reduce (fn [s [k v]]
            (str/replace s (str "${" k "}") (str v)))
          template
          vars))

(defn generate-formula
  "Generates a formula file by substituting vars in a template.
   Creates parent directories if needed."
  [template-path output-path vars]
  (let [template (slurp template-path)
        result (substitute-vars template vars)
        parent (fs/parent output-path)]
    (when parent
      (fs/create-dirs parent))
    (spit output-path result)))
