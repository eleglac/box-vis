(ns box-vis.options
  (:require 
            [cheshire.core :as json]
            [com.rpl.specter :refer :all]
            [org.httpkit.client :as http] 
            )
  )

;; whole lotta DRY violations in this file, but it works and can be refactored later
;; TODO refactor later

(def auth-token (System/getenv "TRADIER_AUTH_TOKEN"))

(def api-root "https://sandbox.tradier.com/v1/markets")

(def expirations (str api-root "/options/expirations"))

(def chains (str api-root "/options/chains"))

(def default-headers
  {"Authorization" (str "Bearer " auth-token) 
   "Accept" "application/json"})

(def very-serious-user-agent-string "Options Boiiiiii 0.0.1-alpha")

(def useful-columns [:description :symbol :underlying :option_type :expiration_date :strike :open :high :low :close :volume :asksize :bidsize :open_interest])

(def min-columns [:symbol :open :high :low :close :volume :asksize :bidsize :open_interest])

(def mesh-columns [:symbol :strike :expiration_date :close])

(defn get-expirations-promise [ticker]
  (http/get 
    expirations
    {
     :headers default-headers
     :query-params {"symbol" ticker}
     :user-agent very-serious-user-agent-string
     :as :text
     }
    (fn [{:keys [status headers body error] :as resp}]
      (if error
        (println "Failed, exception is " error)
        body))))

(defn parse-expirations [exp-promise]
  (select [:expirations :date ALL] (json/parse-string @exp-promise true)))

(defn get-chain-promise [ticker expire-date]
  (http/get 
    chains
    {:headers default-headers
     :query-params {"symbol" ticker "expiration" expire-date}
     :user-agent very-serious-user-agent-string
     :as :text
     }
    (fn [{:keys [status headers body error] :as resp}]
      (if error
        (println "Failed, exception is " error)
        body))))

(defn parse-chain [chain-promise]
  (select [:options :option ALL] (json/parse-string @chain-promise true)))

(defn get-full-chain [chain-promises]
  (map parse-chain chain-promises))
