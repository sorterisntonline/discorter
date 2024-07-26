(ns electric-starter-app.discord
  (:require
   [clojure.edn :as edn]
   [clojure.core.async :as a])
  (:import [org.eclipse.jetty.websocket.client WebSocketClient]
           [org.eclipse.jetty.websocket.api Session WebSocketAdapter]
           [java.net URI]))

(defn create-socket []
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [^Session session]
      (println "WebSocket Connected"))
    
    (onWebSocketText [^String message]
      (println "Received message:" message))
    
    (onWebSocketClose [statusCode reason]
      (println "WebSocket Closed. Status:" statusCode "Reason:" reason))
    
    (onWebSocketError [^Throwable cause]
      (println "WebSocket Error:" (.getMessage cause)))))

(defn connect-websocket [url]
  (let [client (WebSocketClient.)
        socket (create-socket)]
    (try
      (.start client)
      (let [future (.connect client socket (URI. url))
            session (.get future)]
        (def session session)
        (.. session (getRemote) (sendString "rst")))
      (catch Exception e
        (println "Error:" (.getMessage e)))
      (finally
        ;; In a real application, you'd keep the client running and close it when done
        ;; (.stop client)
        ))))

;; Usage
(comment
  (connect-websocket "wss://gateway.discord.gg")
  org.eclipse.jetty.websocket.common.WebSocketSession)
