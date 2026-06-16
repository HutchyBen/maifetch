;; AI-assisted with OpenAI GPT-5 Codex.
(ns maifetch.core
  (:gen-class)
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.net URI URL]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]
           [java.time Duration]
           [javax.imageio ImageIO]))

(def default-base-url "https://maitea.app")
(def ascii-ramp "@%#*+=-:. ")

(defn getenv [name]
  (System/getenv name))

(defn blank? [value]
  (or (nil? value) (str/blank? (str value))))

(defn parse-int-value [label value]
  (try
    (Integer/parseInt (str value))
    (catch Exception _
      (throw (ex-info (str label " must be an integer") {:label label :value value})))))

(defn consume-value [args flag]
  (let [value (second args)]
    (when (blank? value)
      (throw (ex-info (str flag " requires a value") {:flag flag})))
    [value (nnext args)]))

(defn parse-cli [args]
  (loop [remaining (seq args)
         opts {}]
    (if-not remaining
      opts
      (let [arg (first remaining)]
        (cond
          (= arg "--help")
          (recur (next remaining) (assoc opts :help true))

          (str/starts-with? arg "--access-token=")
          (recur (next remaining) (assoc opts :accessToken (subs arg 15)))

          (#{"--access-token" "-a" "-t"} arg)
          (let [[value more] (consume-value remaining arg)]
            (recur more (assoc opts :accessToken value)))

          (str/starts-with? arg "--logo-size=")
          (recur (next remaining) (assoc opts :logoSize (parse-int-value "logo size" (subs arg 12))))

          (#{"--logo-size" "-l"} arg)
          (let [[value more] (consume-value remaining arg)]
            (recur more (assoc opts :logoSize (parse-int-value "logo size" value))))

          (str/starts-with? arg "--score-count=")
          (recur (next remaining) (assoc opts :scoreCount (parse-int-value "score count" (subs arg 14))))

          (#{"--score-count" "-s"} arg)
          (let [[value more] (consume-value remaining arg)]
            (recur more (assoc opts :scoreCount (parse-int-value "score count" value))))

          (str/starts-with? arg "--config-file=")
          (recur (next remaining) (assoc opts :configFile (subs arg 14)))

          (#{"--config-file" "-c"} arg)
          (let [[value more] (consume-value remaining arg)]
            (recur more (assoc opts :configFile value)))

          (str/starts-with? arg "--base-url=")
          (recur (next remaining) (assoc opts :baseUrl (subs arg 11)))

          (= arg "--base-url")
          (let [[value more] (consume-value remaining arg)]
            (recur more (assoc opts :baseUrl value)))

          :else
          (throw (ex-info (str "unknown option: " arg) {:arg arg})))))))

(defn user-config-dir []
  (let [home (System/getProperty "user.home")
        os-name (str/lower-case (System/getProperty "os.name" ""))]
    (cond
      (not (blank? (getenv "APPDATA")))
      (getenv "APPDATA")

      (str/includes? os-name "mac")
      (str home File/separator "Library" File/separator "Application Support")

      (not (blank? (getenv "XDG_CONFIG_HOME")))
      (getenv "XDG_CONFIG_HOME")

      :else
      (str home File/separator ".config"))))

(defn default-config-path []
  (str (user-config-dir) File/separator "maifetch.json"))

(defn read-json-file [path]
  (when (and (not (blank? path)) (.exists (io/file path)))
    (with-open [reader (io/reader path)]
      (json/read reader :key-fn keyword))))

(defn env-config []
  (let [base-url (or (not-empty (getenv "MAITEA_BASE_URL"))
                     (not-empty (getenv "MAIFETCH_BASE_URL")))]
    (cond-> {}
      (not (blank? (getenv "MAITEA_TOKEN")))
      (assoc :accessToken (getenv "MAITEA_TOKEN"))

      (not (blank? (getenv "MAITEA_SCORE_COUNT")))
      (assoc :scoreCount (parse-int-value "score count" (getenv "MAITEA_SCORE_COUNT")))

      (not (blank? (getenv "MAITEA_LOGO_SIZE")))
      (assoc :logoSize (parse-int-value "logo size" (getenv "MAITEA_LOGO_SIZE")))

      (not (blank? (getenv "MAITEA_CONFIG_FILE")))
      (assoc :configFile (getenv "MAITEA_CONFIG_FILE"))

      (not (blank? base-url))
      (assoc :baseUrl base-url))))

(defn normalize-config [config]
  (-> config
      (update :logoSize #(parse-int-value "logo size" (or % 20)))
      (update :scoreCount #(parse-int-value "score count" (or % 4)))))

(defn validate-config [config]
  (cond
    (blank? (:accessToken config))
    (throw (ex-info "access token is required" {:field :accessToken}))

    (< (:scoreCount config) 1)
    (throw (ex-info "score count must be at least 1" {:field :scoreCount}))

    (> (:scoreCount config) 12)
    (throw (ex-info "score count cannot be higher than 12" {:field :scoreCount}))

    :else
    config))

(defn load-config [args]
  (let [cli (parse-cli args)
        env (env-config)
        config-path (or (:configFile cli) (:configFile env) (default-config-path))
        file-config (read-json-file config-path)]
    (-> {:logoSize 20
         :scoreCount 4
         :baseUrl default-base-url
         :configFile config-path}
        (merge file-config env cli)
        (dissoc :help)
        normalize-config
        validate-config)))

(defn read-json-string [body]
  (json/read-str body :key-fn keyword))

(defn response-data [payload]
  (if (and (map? payload) (contains? payload :data))
    (:data payload)
    payload))

(defn base-url [config]
  (str/replace (:baseUrl config default-base-url) #"/+$" ""))

(defn request-json [config path]
  (let [client (HttpClient/newHttpClient)
        request (-> (HttpRequest/newBuilder (URI/create (str (base-url config) path)))
                    (.timeout (Duration/ofSeconds 30))
                    (.header "Authorization" (str "Bearer " (:accessToken config)))
                    (.header "Content-Type" "application/json")
                    (.header "Accept" "application/json")
                    (.GET)
                    (.build))
        response (.send client request (HttpResponse$BodyHandlers/ofString))
        status (.statusCode response)]
    (when-not (<= 200 status 299)
      (throw (ex-info (str "MaiTea API returned HTTP " status) {:status status :path path})))
    (response-data (read-json-string (.body response)))))

(defn fetch-resource [config path fixture-env]
  (if-let [fixture (not-empty (getenv fixture-env))]
    (response-data (read-json-file fixture))
    (request-json config path)))

(defn wide-to-normal [text]
  (apply str
         (map (fn [ch]
                (let [code (int ch)]
                  (if (<= 0xFF01 code 0xFF5E)
                    (char (- code 0xFEE0))
                    ch)))
              (str text))))

(defn difficulty-string [difficulty]
  (case (str/lower-case (str difficulty))
    "easy" "Easy"
    "basic" "Basic"
    "advanced" "Advanced"
    "expert" "Expert"
    "master" "Master"
    "remaster" "Re:Master"
    "re:master" "Re:Master"
    "utage" "Utage"
    (str difficulty)))

(defn rank-string [rank]
  (str rank))

(defn profile-lines [profile plays score-count]
  (let [name (wide-to-normal (:name profile))
        header [(str name)
                (apply str (repeat (count name) "-"))
                (format "ID: %d" (long (:id profile 0)))
                (format "Rating: %.2f / %.2f"
                        (/ (double (:rating profile 0)) 100.0)
                        (/ (double (:rating_highest profile 0)) 100.0))
                (format "Level: %d" (long (:level profile 0)))
                (format "Total Credits: %d" (long (get-in profile [:play_stats :total] 0)))
                "Recent Scores:"]
        score-lines (mapcat
                     (fn [play]
                       (let [song (get-in play [:song :name :en] "")
                             difficulty (difficulty-string (get-in play [:difficulty_level :value] ""))
                             fc-label (or (:full_combo_label play) "")
                             score-line (str "  " (:score_formatted play "") " "
                                             (:achievement_formatted play "") "% "
                                             (rank-string (:rank play ""))
                                             (when-not (blank? fc-label) (str " " fc-label)))]
                         [(str "  " song "  " difficulty)
                          score-line
                          ""]))
                     (take score-count plays))]
    (vec (concat header score-lines))))

(defn scale-coordinate [source-size target-size index]
  (int (Math/floor (* (/ (double index) (max 1 target-size)) source-size))))

(defn brightness->char [brightness]
  (let [idx (int (Math/floor (* (/ (double brightness) 256.0) (count ascii-ramp))))]
    (nth ascii-ramp (min (dec (count ascii-ramp)) idx))))

(defn image->ascii [url size]
  (with-open [stream (.openStream (URL. url))]
    (let [image (ImageIO/read stream)
          width (* size 2)
          height size
          source-width (.getWidth image)
          source-height (.getHeight image)]
      (for [y (range height)]
        (apply str
               (for [x (range width)]
                 (let [source-x (scale-coordinate source-width width x)
                       source-y (scale-coordinate source-height height y)
                       rgb (.getRGB image source-x source-y)
                       red (bit-and (bit-shift-right rgb 16) 0xFF)
                       green (bit-and (bit-shift-right rgb 8) 0xFF)
                       blue (bit-and rgb 0xFF)
                       brightness (/ (+ (* 0.299 red) (* 0.587 green) (* 0.114 blue)) 1.0)]
                   (brightness->char brightness))))))))

(defn combine-logo [info-lines logo-lines logo-size]
  (let [max-lines (max (count info-lines) (count logo-lines))
        empty-logo (apply str (repeat (* logo-size 2) " "))]
    (mapv (fn [idx]
            (str (get logo-lines idx empty-logo)
                 "  "
                 (get info-lines idx "")))
          (range max-lines))))

(defn render-output [profile plays logo-size score-count]
  (let [info-lines (profile-lines profile plays score-count)]
    (if (pos? logo-size)
      (let [logo-url (get-in profile [:options :icon :png])
            logo-lines (if (blank? logo-url) [] (vec (image->ascii logo-url logo-size)))]
        (combine-logo info-lines logo-lines logo-size))
      info-lines)))

(def usage
  (str "Usage: maifetch [options]\n"
       "  --access-token, -a, -t TOKEN   MaiTea access token\n"
       "  --logo-size, -l SIZE           ASCII logo size; 0 disables\n"
       "  --score-count, -s COUNT        recent scores to display, max 12\n"
       "  --config-file, -c PATH         JSON config file\n"
       "  --base-url URL                 override MaiTea API base URL for tests"))

(defn run [args]
  (let [cli (parse-cli args)]
    (if (:help cli)
      (println usage)
      (let [config (load-config args)
            profiles (fetch-resource config "/api/v1/profiles" "MAIFETCH_PROFILE_FIXTURE")
            plays (fetch-resource config "/api/v1/plays" "MAIFETCH_PLAYS_FIXTURE")]
        (if (empty? profiles)
          (println "No profiles found")
          (doseq [line (render-output (first profiles) plays (:logoSize config) (:scoreCount config))]
            (println line)))))))

(defn -main [& args]
  (try
    (run args)
    (catch Exception ex
      (binding [*out* *err*]
        (println (.getMessage ex)))
      (System/exit 1))))
