(ns sse-server.core
  (:require [org.httpkit.server :refer [run-server with-channel on-close send! close]]
            [org.httpkit.timer :refer [schedule-task]]))

(def channel-hub (atom {}))

(defn handle-cors []
  {:status 204
   :headers {"Access-Control-Allow-Origin" "*"
             "Connection" "close"}})

(defn handle-404 []
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "File not found"})

(defn handle-connections []
  {:status 200
   :headers {"Content-Type" "text/plain"
             "Access-Control-Allow-Origin" "*"
             "Cache-Control" "no-cache"
             "Connection" "close"}
   :body (str (count @channel-hub))})


(defn handle-sse [req]
  (with-channel req channel
    (send! channel {:status 200
                    :headers {"Content-Type" "text/event-stream"
                              "Access-Control-Allow-Origin" "*"
                              "Cache-Control" "no-cache"
                              "Connection" "keep-alive"}
                    :body ":ok\n\n"} false)
    (swap! channel-hub assoc channel req)
    (on-close channel (fn [_]
                        (swap! channel-hub dissoc channel)))))

(defn app [req]
  (if (= (:request-method req) :options)
    (handle-cors)
    (case (:uri req)
      "/connections" (handle-connections)
      "/sse" (handle-sse req)
      (handle-404))))

(defn notify-channels []
  (let [time (System/currentTimeMillis)]
    (doseq [channel (keys @channel-hub)]
      (send! channel (str "data: " time "\n\n") false))))

(defn start-timer []
  (schedule-task 1000
                 (notify-channels)
                 (start-timer)))

(defn parse-port [args]
  (if (= (count args) 0)
    1942
    (Integer. (first args))))

(defn -main
  [& args]
  (let [port (parse-port args)]
    (run-server app {:port port :thread 8 :queue-size 300000})
    (start-timer)
    (print (str "Listening on http://127.0.0.1:" port))))
