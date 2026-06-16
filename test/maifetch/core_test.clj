;; AI-assisted with OpenAI GPT-5 Codex.
(ns maifetch.core-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [maifetch.core :as core]))

(defn fake-env [values]
  (fn [name] (get values name)))

(def profile-fixture
  (.getPath (io/file "test" "fixtures" "profile.json")))

(def plays-fixture
  (.getPath (io/file "test" "fixtures" "plays.json")))

(deftest fixture-rendering
  (with-redefs [core/getenv (fake-env {"MAIFETCH_PROFILE_FIXTURE" profile-fixture
                                       "MAIFETCH_PLAYS_FIXTURE" plays-fixture})]
    (let [output (with-out-str
                   (core/run ["--access-token" "fixture-token"
                              "--logo-size" "0"
                              "--score-count" "2"]))]
      (doseq [needle ["MAI"
                      "ID: 42"
                      "Rating: 12.34 / 15.00"
                      "Level: 17"
                      "Total Credits: 99"
                      "Test Song  Master"
                      "1,000,000 100.5000% SSS+ FC"
                      "Second Song  Expert"
                      "999,999 99.9999% SS"]]
        (is (str/includes? output needle))))))

(deftest config-precedence
  (let [config-file (doto (java.io.File/createTempFile "maifetch" ".json")
                      (.deleteOnExit))]
    (spit config-file "{\"accessToken\":\"file-token\",\"logoSize\":9,\"scoreCount\":1}")
    (with-redefs [core/getenv (fake-env {"MAITEA_TOKEN" "env-token"
                                         "MAITEA_SCORE_COUNT" "3"
                                         "MAITEA_LOGO_SIZE" "2"})]
      (let [config (core/load-config ["--config-file" (.getPath config-file)
                                      "--access-token" "cli-token"
                                      "--logo-size" "0"])]
        (is (= "cli-token" (:accessToken config)))
        (is (= 0 (:logoSize config)))
        (is (= 3 (:scoreCount config)))))))

(deftest validation-errors
  (with-redefs [core/getenv (constantly nil)
                core/default-config-path (constantly "missing-maifetch-config.json")]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"access token is required"
                          (core/load-config []))))
  (with-redefs [core/getenv (fake-env {"MAITEA_TOKEN" "token"})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"score count cannot be higher than 12"
                          (core/load-config ["--score-count" "13"])))))

(defn -main [& _]
  (let [result (run-tests 'maifetch.core-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
