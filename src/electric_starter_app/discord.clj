(ns electric-starter-app.discord
  (:require
   [clojure.edn :as edn]
   [clojure.core.async :as a]
   [clojure.data.json :as json]
   [hato.client :as hc])
  
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
(def session-id (atom nil))

(def messages (atom []))
(reset! messages [])

(defn send-data [d]
  (println (str "sent data:" d))
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

(defn respond-hello [time]
  (println "time" time)
  (when-not @heartbeater
    (reset! heartbeater (future (loop [] (Thread/sleep (or 5000 time))
                                      (if (.isOpen @session)
                                        (do (send-heartbeat)
                                            (recur))
                                        (println "heartbeater dying"))))))
  
  (if (and @resume-url @last-sequence-number @session-id)
    (do
      (println "resuming")
      (send-data {:op 6 :d {:token TOKEN :session_id @session-id :seq @last-sequence-number}}))
    (do
      (println "starting new connection")
      (send-heartbeat)
      (Thread/sleep 100)
      (send-data {:op 2 :d {:token TOKEN
                            :intents 32265
                            :properties {:os "linux"
                                         :browser "sorter"
                                         :device "jvm"}
                            :presence {:status "online"
                                       :activities []
                                       :afk false}}}))))

"ux: any channel can be sorted, any message with a react is added to that channels sort

register react as sorter react, when used, bot responds with message

"

(defn is-emoji? [st] (re-matches #"<:[a-zA-Z0-9_]+:[0-9]+>" st))

(defn http-interaction [interaction text]
  (hc/post (str "https://discord.com/api/v10/interactions/"
                (:id interaction) "/" (:token interaction) "/callback")
           {:headers {"Content-type" "application/json"}
            :body (json/write-str
                   {:type 4
                    :data {:content text}})}))

(defn respond-interaction [interaction]
  (def interaction interaction)
  (cond
    (= (:type interaction) 2)
    
    (if (is-emoji? (-> interaction :data :options first :value))
      (http-interaction interaction (str "react " (-> interaction :data :options first :value) "to any message to add it to sorter"))
      (http-interaction interaction "invalid emoji, try a custom server emoji"))
    
    true :nothing))

(defn handle-event [message]
  (def message message)
  (case (:t message)
    "GUILD_CREATE" :nothing
    "READY" (do
              (reset! session-id (:session_id (:d message)))
              (reset! resume-url (:resume_gateway_url (:d message))))
    "MESSAGE_CREATE" (do
                       (swap! messages conj (:d message)))
    "INTERACTION_CREATE" (respond-interaction (:d message))
    :nothing))


(defn create-socket []
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [^Session session]
      (println "WebSocket Connected"))
    
    (onWebSocketText [^String message]
      (let [message (json/read-str message {:key-fn keyword})]
        (prn "Received message:" message)
        
        (when (:s message) (reset! last-sequence-number (:s message)))
        
        (case (:op message)
          10 (respond-hello (:heartbeat_interval (:d message)))
          11 :nothing
          0 (prn (handle-event message))
          
          :nothing)))
    
    (onWebSocketClose [statusCode reason]
      (println "WebSocket Closed. Status:" statusCode "Reason:" reason))
    
    (onWebSocketError [^Throwable cause]
      (println "WebSocket Error:" (.getMessage cause)))))

(defn connect-websocket []
  (let [url (or @resume-url
                "wss://gateway.discord.gg")
        client (WebSocketClient.)
        socket (create-socket)]
    
    (.start client)
    (let [future (.connect client socket (URI. url))
          sesh (.get future)]
      (reset! session sesh))))

(defn close []
  (.close @session)
  (future-cancel @heartbeater))

(defn start []
  (hc/post (str "https://discord.com/api/v10/applications/" APP-ID "/commands")
           {:headers {"Content-type" "application/json"
                      "Authorization" (str "Bot " TOKEN) }
            :body (json/write-str
                   {:name "register_emoji"
                    :type 1
                    :description "which emoji should be used to mark items to be sorted"
                    :options [{:type 3
                               :name "emoji"
                               :description "which emoji should be used to add message to sorter"}]})})
  (connect-websocket))


;; Usage
(comment
  (connect-websocket)

  (close))
