(ns clj-packet.core
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer :all]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [join]])
  (:gen-class))

(set! *warn-on-reflection* 1)

(timbre/refer-timbre)
;;(timbre/merge-config! {:appenders {:println {:enabled? true}}})

;; Inspired by https://github.com/clojurewerkz/elastisch/blob/master/src/clojurewerkz/elastisch/rest.clj


(def base-url "https://api.packet.net")
(def orgid "60bdbe9f-10c2-4ac9-bc75-6fd635f52daa")
(def projectid "e40d191d-4394-477c-b47d-2f08cc26a98a")

(def options {:headers {"Content-Type" "application/json"}})

(defn parse-safely [json]
  (try
    (decode json true)
    (catch Exception e
      (throw (ex-info "InvalidJSON"
                      {:message (str "Failed to parse " json)
                       :reason (.getMessage e)})))))

(defn get!
  ([^String uri apikey]
   (timbre/info "Sending GET to " uri)
   (parse-safely
    (:body @(http/get uri (merge options
                                 {:query-params  {:token apikey}}))))))

(defn post!
  [uri {:keys [body apikey]}]
  (timbre/info "Sending post to " uri)
  (timbre/spy body)
  (let [{:keys [status error] :as resp} @(http/post uri (merge options
                                                               {:body (encode body)
                                                                :query-params  {:token apikey}}))]
    (if error
      (timbre/error "Failed, exception: " error)
      (timbre/info "Status " resp))))

(defn url-with-path [& segments]
  (str base-url "/"  (join "/" segments)))

;; Organization

(defn get-organizations
  ([k]
   (get! (url-with-path "organizations") k))
  ([id k]
   (get! (url-with-path "organizations" id) k))
  ([id s k]
   (get! (url-with-path "organizations" id s) k)))

(defn get-organizations-invitations [id key]
  (:invitations (get-organizations id "invitations" key)))

(defn create-organizations-invitation [org email role proj key]
  (let [org-name (:name (get-organizations org))]
    (post! (url-with-path "organizations" org "invitations")
           {:body {:invitee email
                   :roles [role]
                   :organization_id [orgid]}
            :apikey key})))


;; Example Invitation
(create-organizations-invitation orgid "foo@bar.com" "owner" projectid authToken)

;; List invitations
(pprint (get-organizations-invitations orgid authToken))

;; Note authToken is private
