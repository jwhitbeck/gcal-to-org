;; Copyright (c) 2017 John Whitbeck. All rights reserved.
;;
;; The use and distribution terms for this software are covered by the
;; Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt)
;; which can be found in the file al-v20.txt at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;;
;; You must not remove this notice, or any other, from this software.

(ns gcto.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :as str]
            [cljs.core.async :as a :refer [<!]]
            [cljs.nodejs :as node]
            [cljs.spec.alpha :as s]
            [cljs.reader :as edn]
            [cljs.tools.cli :as cli]
            [goog.date :as date]))

(def fs (node/require "fs"))
(def path (node/require "path"))
(def http (node/require "http"))
(def child-process (node/require "child_process"))
(def url (node/require "url"))
(def querystring (node/require "querystring"))
(def google (node/require "googleapis"))

(defn- panic [msg]
  (binding [*print-fn* *print-err-fn*]
    (println msg)
    (.exit js/process 1)))

(s/def ::config (s/keys :opt-un [::title ::filetags ::tags ::categories ::calendar]))

(defn- no-whitespace? [s] (not (str/includes? s " ")))

(s/def ::tag (s/and string? no-whitespace?))
(s/def ::title string?)
(s/def ::filetags (s/coll-of ::tag))
(s/def ::tags (s/coll-of ::tag))

(s/def ::categories (s/keys :opt-un [::all-day ::appointment]))

(def default-categories {:all-day "All day" :appointment "Appointment"})

(s/def ::calendars (s/and (s/coll-of ::calendar) seq))
(s/def ::calendar (s/keys :req-un [::id] :opt-un [::from-days ::to-days ::tags]))
(s/def ::id string?)
(s/def ::from-days integer?)
(s/def ::to-days integer?)

(def default-calendar {:from-days -10 :to-days 30})

(defn- read-config [file]
  (if-not (.existsSync fs file)
    (panic "Config file does not exist")
    (let [cfg (edn/read-string (.readFileSync fs file "utf8"))]
      (if-not (s/valid? ::config cfg)
        (panic (str "Invalid configuration file.\n" (s/explain-str ::config cfg)))
        (-> cfg
            (update :categories (partial merge default-categories))
            (update :calendars (partial map (partial merge default-calendar))))))))

(defn- context [config]
  (assoc config
         :now (date/DateTime.)
         :open "xdg-open"
         :client-id "446550967979-2b0mme6g0j6g2l4i49lb5ni1qq0fjo8i.apps.googleusercontent.com"
         :client-secret "qdDcAyHZyjEDPikatIlaEOF9"
         :scope "https://www.googleapis.com/auth/calendar.readonly"
         :credentials-file (.join path (aget js/process.env "HOME") ".cache" "gcal-to-org" "credentials.json")))

;;; Google OAUTH2 workflow: https://developers.google.com/identity/protocols/OAuth2InstalledApp
;;; 1. Get an auth code
;;; 2. Use it to retrieve an access token and refresh token pair

(defn- get-new-tokens [ct]
  (let [{:keys [client-id client-secret scope open]} ct
        code-ch (a/chan)
        close-ch (a/chan)
        handler (fn [req resp]
                  (a/close! close-ch)
                  (let [qs (.-query (.parse url (.-url req)))
                        code (aget (.parse querystring qs) "code")]
                    (when code
                      (a/put! code-ch code)
                      (a/close! code-ch)
                      (.write resp "This window may be safely closed.")
                      (.end resp))))
        server (doto (.createServer http handler) .listen)
        uri (str "http://localhost:" (.-port (.address server)))
        oauth2-client (google.auth.OAuth2. client-id client-secret uri)
        oauth2-url (.generateAuthUrl oauth2-client (js-obj "access_type" "offline" "scope" scope))]
    (.spawn child-process open (into-array [oauth2-url]))
    (go (<! close-ch) (.close server))
    (go (let [ch (a/chan)
              code (<! code-ch)]
          (.getToken oauth2-client code (fn [err tokens]
                                          (when err (panic err))
                                          (a/put! ch tokens)
                                          (a/close! ch)))
          (<! ch)))))

(defn- make-parents! [p]
  (doseq [dir (->> (iterate #(.-dir (.parse path %)) p)
                   rest
                   (take-while #(not (.existsSync fs %)))
                   reverse)]
    (.mkdirSync fs dir)))

(defn- read-tokens [ct]
  (let [{:keys [credentials-file]} ct]
    (when (.existsSync fs credentials-file)
      (js/JSON.parse (.readFileSync fs credentials-file)))))

(defn- write-tokens! [ct tokens]
  (let [{:keys [credentials-file]} ct]
    (make-parents! credentials-file)
    (.writeFileSync fs credentials-file (js/JSON.stringify tokens))))

(defn- get-descr [ct gcal calendar]
  (let [ch (a/chan)]
    (.get (.-calendars gcal) (js-obj "calendarId" (:id calendar))
          (fn [err resp]
            (when err (panic err))
            (a/put! ch resp)
            (a/close! ch)))
    ch))

(defn- add-days [dt n] (doto (.clone dt) (.add (date/Interval. 0 0 n))))

(defn- get-events [ct gcal calendar]
  (let [{:keys [now]} ct
        {:keys [from-days to-days id]} calendar
        page-ch (a/chan)
        ch (a/chan 1 cat)
        min-time (.toUTCRfc3339String (add-days now from-days))
        max-time (.toUTCRfc3339String (add-days now to-days))]
    (go-loop [page-tok nil]
      (let [resp-ch (a/chan)]
        (.list (.-events gcal) (js-obj "calendarId" id "timeMin" min-time "timeMax" max-time
                                       "pageToken" page-tok "singleEvents" true)
               (fn [err resp]
                 (when err (panic err))
                 (a/put! resp-ch resp)
                 (a/close! resp-ch)))
        (let [resp (<! resp-ch)]
          (>! page-ch (aget resp "items"))
          (if-let [tok (aget resp "nextPageToken")]
            (recur tok)
            (a/close! page-ch)))))
    (a/pipe page-ch ch)
    ch))

(def dow (into-array ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"]))

(defn- parse-date [s]
  (let [[y m d] (str/split s "-")]
    (date/Date. (js/parseInt y) (js/parseInt m) (js/parseInt d))))

(defn- pad2 [n] (str (when (< n 10) "0") n))

(defn- fmt-date [dt]
  (str (.getFullYear dt) "-" (pad2 (inc (.getMonth dt))) "-" (pad2 (.getDate dt))
       " " (aget dow (.getWeekday dt))))

(defn- fmt-time [dt]
  (str (pad2 (.getHours dt)) ":" (pad2 (.getMinutes dt))))

(defn- parse-date-time [s]
  (date/DateTime.fromRfc822String s))

(defn- org-all-day-timestamp [evt]
  (let [start (-> evt (aget "start") (aget "date") parse-date)
        end (-> evt (aget "end") (aget "date") parse-date (add-days -1))]
    (if (zero? (date/Date.compare start end))
      (str "<" (fmt-date start) ">")
      (str "<" (fmt-date start) ">--<" (fmt-date end) ">"))))

(defn- org-appointment-timestamp [evt]
  (let [start (-> evt (aget "start") (aget "dateTime") parse-date-time)
        end (-> evt (aget "end") (aget "dateTime") parse-date-time)]
    (if (and (= (.getFullYear start) (.getFullYear end))
             (= (.getMonth start)) (.getMonth end)
             (= (.getDate start) (.getDate end)))
      (str "<" (fmt-date start) " " (fmt-time start) "-" (fmt-time end) ">")
      (str "<" (fmt-date start) " " (fmt-time start) ">--<" (fmt-date end) " " (fmt-time end) ">"))))

(defn- category [evt]
  (let [start (aget evt "start")
        end (aget evt "end")]
    (cond
      (and (aget start "date") (aget end "date")) :all-day
      (and (aget start "dateTime") (aget end "dateTime")) :appointment
      :else (panic "Event has both date and date-time bounds. Not currently supported."))))

(defn- org-timestamp [evt]
  (case (category evt)
    :all-day (org-all-day-timestamp evt)
    :appointment (org-appointment-timestamp evt)))

(defn- print-file-header [ct]
  (let [{:keys [title filetags tags now]} ct]
    (when title
      (println "#+TITLE:" title))
    (when (seq filetags)
      (println "#+FILETAGS:" (str/join " " filetags)))
    (when (seq tags)
      (println "#+TAGS:" (str/join " " tags)))
    (println "#+COMMENT: Generated on" (.toIsoString now) "by gcal-to-org. Do not edit manually.")
    (println)))

(defn- print-calendar-header [ct calendar descr]
  (let [{:keys [tags]} calendar]
    (println "*" (aget descr "summary") (if (seq tags) (str ":" (str/join ":" tags) ":") ""))
    (when-let [body (aget descr "description")]
      (println body))))

(defn- person [p]
  (if-let [name (aget p "displayName")]
    (str name " <" (aget p "email") ">")
    (str "<" (aget p "email") ">")))

(defn- print-event [ct evt]
  (println "**" (aget evt "summary"))
  (println ":PROPERTIES:")
  (println ":CATEGORY:" ((category evt) (:categories ct)))
  (println ":status:" (aget evt "status"))
  (println ":hangout-link" (aget evt "hangoutLink"))
  (println ":html-link:" (aget evt "htmlLink"))
  (println ":creator:" (person (aget evt "creator")))
  (println ":created-at:" (aget evt "created"))
  (println ":attendees:" (str/join ", " (map person (aget evt "attendees"))))
  (println ":END:")
  (println (org-timestamp evt)))

(defn- run [ct]
  (go (let [{:keys [client-id client-secret calendars]} ct
            tokens (or (read-tokens ct) (<! (get-new-tokens ct)))
            auth (doto (google.auth.OAuth2. client-id client-secret)
                   (.setCredentials tokens))
            gcal (.calendar google (js-obj "version" "v3" "auth" auth))
            ;; Fetch the calendar description and events in parallel.
            cals (doall (for [cal calendars]
                          {:calendar cal
                           :descr-ch (get-descr ct gcal cal)
                           :events-ch (get-events ct gcal cal)}))]
        (print-file-header ct)
        (doseq [{:keys [calendar descr-ch events-ch]} cals]
          (print-calendar-header ct calendar (<! descr-ch))
          (loop [evt (<! events-ch)]
            (when evt
              (print-event ct evt)
              (recur (<! events-ch)))))
        ;; If the auth tokens were refreshed, store the news ones to disk.
        (let [cur-tokens (.-credentials auth)]
          (when-not (not= (.-expiry_date tokens) (.-expiry_date cur-tokens))
            (write-tokens! ct cur-tokens))))))

(def cli-spec [["-h" "--help" "Display help."]])

(defn -main [& args]
  (let [{:keys [summary options arguments]} (cli/parse-opts args cli-spec)]
    (if (or (:help options) (not= 1 (count arguments)))
      (println (str "Usage: gcto <config file>\n\n" summary))
      (run (context (read-config (first arguments)))))))

(node/enable-util-print!)
(set! *main-cli-fn* -main)
