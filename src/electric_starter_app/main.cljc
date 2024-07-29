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
     (dom/table
      (dom/tbody
       (e/for-by identity [message (e/server (e/watch d/messages))]
                 (dom/tr
                  (dom/style {:background "pink"})
                  (dom/td (dom/pre (dom/text (e/server (pprint-to-str message)))))
                  (dom/td (dom/pre (dom/text (:content message)))))))))))
