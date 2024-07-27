(ns electric-starter-app.main
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            #?(:clj
               [clojure.pprint :as pprint])
            #?(:clj
               [electric-starter-app.discord :as d])))

;; Saving this file will automatically recompile and update in your browser

#?(:clj (defn pprint-to-str [data]
          (let [sw (java.io.StringWriter.)]
            (pprint/write data :stream sw)
            (.toString sw))))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (dom/p (dom/text "discord"))
      (let [])
      (e/for-by identity [line (e/server (e/watch d/log))]
                (dom/pre (dom/text (e/server (pprint-to-str line)))
                         (dom/props {:style {:background-color (if (= 'recvd (first line)) "pink")}}))))))
