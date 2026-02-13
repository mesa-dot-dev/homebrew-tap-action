(ns tap-push.resolve-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [tap-push.resolve :as resolve]))


(deftest resolve-name-test
  (testing "uses explicit input-name"
    (is (= "my-app"
           (resolve/resolve-name {:input-name "my-app"
                                  :github-repository "org/other"}))))

  (testing "extracts repo name from github-repository"
    (is (= "git-fs"
           (resolve/resolve-name {:input-name nil
                                  :github-repository "mesa-dot-dev/git-fs"}))))

  (testing "handles blank input-name"
    (is (= "git-fs"
           (resolve/resolve-name {:input-name ""
                                  :github-repository "mesa-dot-dev/git-fs"})))))


(deftest resolve-version-test
  (testing "uses explicit input-version"
    (is (= "1.2.3"
           (resolve/resolve-version {:input-version "1.2.3"
                                     :github-ref-name "v9.9.9"}))))

  (testing "strips v prefix from github-ref-name"
    (is (= "1.2.3"
           (resolve/resolve-version {:input-version nil
                                     :github-ref-name "v1.2.3"}))))

  (testing "uses ref-name as-is without v prefix"
    (is (= "1.2.3-alpha.1"
           (resolve/resolve-version {:input-version nil
                                     :github-ref-name "1.2.3-alpha.1"}))))

  (testing "returns nil when no version available"
    (is (nil? (resolve/resolve-version {:input-version nil
                                        :github-ref-name nil})))))


(deftest class-s-test
  (testing "simple hyphenated name"
    (is (= "GitFs" (resolve/class-s "git-fs"))))

  (testing "versioned name"
    (is (= "GitFsAT123" (resolve/class-s "git-fs@1.2.3"))))

  (testing "pre-release version"
    (is (= "GitFsAT123Alpha1" (resolve/class-s "git-fs@1.2.3-alpha.1"))))

  (testing "single word"
    (is (= "Foo" (resolve/class-s "foo"))))

  (testing "underscore separator"
    (is (= "FooBar" (resolve/class-s "foo_bar"))))

  (testing "plus in name"
    (is (= "Cxx" (resolve/class-s "c++"))))

  (testing "at-version shorthand"
    (is (= "OpensslAT3" (resolve/class-s "openssl@3"))))

  (testing "pre-release version starting with zero"
    (is (= "GitFsAT012Alpha1" (resolve/class-s "git-fs@0.1.2-alpha.1")))))


(deftest resolve-repo-url-test
  (testing "builds URL from github-repository"
    (is (= "https://github.com/mesa-dot-dev/git-fs"
           (resolve/resolve-repo-url {:github-repository "mesa-dot-dev/git-fs"}))))

  (testing "returns nil when github-repository is blank"
    (is (nil? (resolve/resolve-repo-url {:github-repository nil}))))

  (testing "returns nil when github-repository is empty string"
    (is (nil? (resolve/resolve-repo-url {:github-repository ""})))))
