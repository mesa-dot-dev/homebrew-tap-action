(ns tap-push.template-test
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [tap-push.template :as template]))


(deftest detect-required-vars-test
  (testing "detects ${VAR} syntax"
    (let [content (str "class ${FORMULA_CLASS_NAME} < Formula\n"
                       "  url \"${URL}\"\n"
                       "  version \"${VERSION}\"\n"
                       "  sha256 \"${SHA256}\"\n"
                       "end")]
      (is (= #{"FORMULA_CLASS_NAME" "URL" "VERSION" "SHA256"}
             (template/detect-required-vars content)))))

  (testing "returns empty set for template with no variables"
    (is (= #{}
           (template/detect-required-vars "class Foo < Formula\n  url \"https://example.com\"\nend"))))

  (testing "detects $VAR syntax without braces"
    (is (= #{"VERSION" "SHA256"}
           (template/detect-required-vars "version \"$VERSION\"\nsha256 \"$SHA256\""))))

  (testing "does not match partial names like $VERSIONED"
    (is (= #{}
           (template/detect-required-vars "echo $VERSIONED"))))

  (testing "detects REPO_URL variable"
    (is (= #{"REPO_URL" "FORMULA_CLASS_NAME"}
           (template/detect-required-vars "class ${FORMULA_CLASS_NAME} < Formula\n  homepage \"${REPO_URL}\"\nend")))))


(deftest validate-vars-test
  (testing "returns nil when all required vars have values"
    (is (nil? (template/validate-vars
                #{"VERSION" "NAME"}
                {"VERSION" "1.0.0" "NAME" "my-app"}))))

  (testing "returns missing var names when values are blank"
    (is (= ["URL"]
           (template/validate-vars
             #{"VERSION" "URL"}
             {"VERSION" "1.0.0" "URL" ""}))))

  (testing "returns multiple missing vars"
    (is (= #{"URL" "SHA256"}
           (set (template/validate-vars
                  #{"VERSION" "URL" "SHA256"}
                  {"VERSION" "1.0.0" "URL" "" "SHA256" ""}))))))


(deftest substitute-vars-test
  (testing "replaces ${VAR} with values"
    (is (= "version \"1.2.3\""
           (template/substitute-vars
             "version \"${VERSION}\""
             {"VERSION" "1.2.3"}))))

  (testing "replaces multiple variables"
    (is (= "class MyApp < Formula\n  version \"1.0\""
           (template/substitute-vars
             "class ${FORMULA_CLASS_NAME} < Formula\n  version \"${VERSION}\""
             {"FORMULA_CLASS_NAME" "MyApp" "VERSION" "1.0"}))))

  (testing "leaves unreferenced vars alone"
    (is (= "version \"1.0\" ${OTHER}"
           (template/substitute-vars
             "version \"${VERSION}\" ${OTHER}"
             {"VERSION" "1.0"})))))


(deftest generate-formula-test
  (testing "generates formula file from template"
    (let [tmp-dir (str (fs/create-temp-dir {:prefix "test-formula-"}))
          template-path (str tmp-dir "/template.rb")
          output-path (str tmp-dir "/output/Formula/app.rb")
          vars {"FORMULA_CLASS_NAME" "MyApp"
                "VERSION" "1.2.3"
                "SHA256" "abc123"
                "LICENSE" "MIT"}]
      (spit template-path
            (str "class ${FORMULA_CLASS_NAME} < Formula\n"
                 "  version \"${VERSION}\"\n"
                 "  sha256 \"${SHA256}\"\n"
                 "  license \"${LICENSE}\"\n"
                 "end"))
      (template/generate-formula template-path output-path vars)
      (let [result (slurp output-path)]
        (is (str/starts-with? result "class MyApp < Formula"))
        (is (str/includes? result "version \"1.2.3\""))
        (is (str/includes? result "sha256 \"abc123\""))
        (is (str/includes? result "license \"MIT\"")))
      (fs/delete-tree tmp-dir)))

  (testing "strips version line when strip-version? is true"
    (let [tmp-dir (str (fs/create-temp-dir {:prefix "test-strip-"}))
          template-path (str tmp-dir "/template.rb")
          output-path (str tmp-dir "/output/Formula/app.rb")
          vars {"FORMULA_CLASS_NAME" "MyApp"
                "VERSION" "1.2.3"
                "SHA256" "abc123"}]
      (spit template-path
            (str "class ${FORMULA_CLASS_NAME} < Formula\n"
                 "  version \"${VERSION}\"\n"
                 "  sha256 \"${SHA256}\"\n"
                 "end"))
      (template/generate-formula template-path output-path vars :strip-version? true)
      (let [result (slurp output-path)]
        (is (str/starts-with? result "class MyApp < Formula"))
        (is (not (str/includes? result "version")))
        (is (str/includes? result "sha256 \"abc123\"")))
      (fs/delete-tree tmp-dir))))


(deftest strip-version-line-test
  (testing "removes version line with leading whitespace"
    (is (= "class Foo < Formula\n  url \"https://example.com\"\n  sha256 \"abc\"\nend"
           (template/strip-version-line
             "class Foo < Formula\n  url \"https://example.com\"\n  version \"1.2.3\"\n  sha256 \"abc\"\nend"))))

  (testing "preserves content when no version line present"
    (is (= "class Foo < Formula\nend"
           (template/strip-version-line "class Foo < Formula\nend"))))

  (testing "does not strip commented version lines"
    (is (= "# version \"1.0\"\nend"
           (template/strip-version-line "# version \"1.0\"\nend")))))
