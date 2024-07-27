(ns electric-starter-app.discord
  (:require
   [clojure.edn :as edn]
   [clojure.core.async :as a]
   [clojure.data.json :as json])
  (:import [org.eclipse.jetty.websocket.client WebSocketClient]
           [org.eclipse.jetty.websocket.api Session WebSocketAdapter]
           [java.net URI]))

(def APP-ID "1266599133179940874")
(def PUBLIC-KEY "e270e03e1923767654eee8c353d105fb96946d127c4a773f5cd5e00c8fbcd3d3")
(def TOKEN (:token (read-string (slurp "secrets.edn"))))

(def session (atom nil))
(def last-sequence-number (atom nil))
(def heartbeater (atom nil))
(def resume-url (atom nil))
(def log (atom []))

(defn send-data [d]
  (swap! log conj ['sent message])
  
  (.. @session (getRemote) (sendString (json/write-str d))))

(defn send-heartbeat []
  (println "sending heartbeat")
  (try (send-data {:op 1 :d @last-sequence-number})
       (catch Exception e
         (println "Error in heartbeater" e))))

"
t = which event
d = data
s = sequence number
"

(defn respond-hello [message]
  (def message message)
  (Thread/sleep 100)
  (when-not @heartbeater
    (reset! heartbeater (future (loop [] (Thread/sleep (-> message :d :heartbeat_interval))
                                      (when (.isOpen @session)
                                        (send-heartbeat)
                                        (recur))))))
  (Thread/sleep 100)

  (send-data {:op 2 :d {:token TOKEN
                        :intents 8
                        :properties {:os "linux"
                                     :browser "sorter"
                                     :device "jvm"}}}))

(defn create-socket []
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [^Session session]
      (println "WebSocket Connected"))
    
    (onWebSocketText [^String message]
      (let [message (json/read-str message {:key-fn keyword})]
        (prn "Received message:" message)
        (reset! last-sequence-number (:s message))
        (def message
          message)
        (swap! log conj ['recvd message])
        (prn (case (:op message)
               10 (respond-hello message)
               11 :nothing
               0 (reset! resume-url (:resume_gateway_url (:d message)))
               :nothing))))
    
    (onWebSocketClose [statusCode reason]
      (println "WebSocket Closed. Status:" statusCode "Reason:" reason))
    
    (onWebSocketError [^Throwable cause]
      (println "WebSocket Error:" (.getMessage cause)))))

(defn connect-websocket [url]
  (let [client (WebSocketClient.)
        socket (create-socket)]
    
    (.start client)
    (let [future (.connect client socket (URI. url))
          sesh (.get future)]
      (reset! session sesh))))

(defn close []
  (.close @session)
  (future-cancel @heartbeater))

;; Usage
(comment
  (connect-websocket "wss://gateway.discord.gg")

  (.close @session)
  
  org.eclipse.jetty.websocket.common.WebSocketSession)
