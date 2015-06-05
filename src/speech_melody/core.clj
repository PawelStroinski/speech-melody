(ns speech-melody.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.stacktrace :as strace]
            [clojure.tools.logging :as log]
            [speech-melody.generator :refer [generate]]
            [speech-melody.uploader :refer [upload]]
            [speech-melody.downloader :refer [download]]
            [speech-melody.language :refer [text-lang]]
            [langohr.core :as lcore]
            [langohr.channel :as lchannel]
            [langohr.queue :as lqueue]
            [langohr.consumers :as lconsumers]
            [langohr.basic :as lbasic]
            [langohr.confirm :as lconfirm]))

(defn- read-config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

(defmacro ^{:private true} log-err-for [context & forms]
  `(try
     ~@forms
     (catch Exception error#
       (log/error
         (with-out-str
           (println " [x] Error:" (.getMessage error#) (or (ex-data error#) ""))
           (println "     Context:" ~context)
           (print "     Stack trace: ")
           (strace/print-cause-trace error#))))))

(defn- truncate [s max-len]
  (if (> (count s) max-len) (subs s 0 max-len) s))

(defn- make-metadata [{:keys [user] :as msg} {:keys [metadata max-len] :as config}]
  (let [{:keys [text lang] :as textlang} (-> (text-lang (:text msg) config)
                                             (update-in [:text] truncate max-len))]
    (-> (merge msg metadata textlang)
        (update-in [:title] format text)
        (update-in [:author] format user)
        (update-in [:description] format lang))))

(defn- send-done [channel metadata {:keys [done-queue]}]
  (let [default-exchange-name ""
        msg (pr-str metadata)]
    (lbasic/publish channel default-exchange-name done-queue msg {:persistent true})
    (lconfirm/wait-for-confirms-or-die channel)
    (log/info " [x] Sent" msg "to" done-queue)))

(def ^{:private true} soundcard (agent nil))

(defn- handle-todo [config channel {:keys [delivery-tag]} ^bytes payload]
  (log-err-for
    (seq payload)
    (let [msg (-> (String. payload "UTF-8") edn/read-string)
          _ (log/info " [x] Received" msg)
          metadata (make-metadata msg config)
          temp-mp3 (download metadata config)
          _ (log/info " [x] Downloaded to" temp-mp3)
          f (fn [_]
              (log-err-for
                msg
                (let [temp-out (generate temp-mp3 metadata)]
                  (future
                    (log-err-for
                      msg
                      (log/info " [x] Written to" temp-out)
                      (.delete temp-mp3)
                      (let [url (upload temp-out metadata config)]
                        (log/info " [x] Uploaded to" url)
                        (.delete temp-out)
                        (send-done channel (assoc metadata :url url) config)
                        (lbasic/ack channel delivery-tag)))))))]
      (send-off soundcard f))))

(defn -main []
  (let [config (read-config)
        connection (lcore/connect)
        channel (lchannel/open connection)
        {:keys [todo-queue done-queue]} config
        todo-handler (fn [& args] (future (apply handle-todo config args)))]
    (lqueue/declare channel todo-queue {:durable true :auto-delete false})
    (lqueue/declare channel done-queue {:durable true :auto-delete false})
    (lconfirm/select channel)
    (log/info (str " [*] Subscribing to " todo-queue "..."))
    (lconsumers/blocking-subscribe channel todo-queue todo-handler {:auto-ack false})))
