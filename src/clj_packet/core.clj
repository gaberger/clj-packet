(ns clj-packet.core
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer :all]
            [environ.core :refer [env]]
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
(def authToken  (env :authtoken))

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


(defn get-ssh-keys [k]
  (get! (url-with-path "ssh-keys") k))


;; Projects

(defn get-projects
  ([k]
   (get! (url-with-path "projects") k))
  ([id k]
   (get! (url-with-path "projects" id) k))
  ([id k & s]
   (get! (url-with-path "projects" id s) k)))

(defn create-device [project-id hostname plan operating_system quantity api-key]
  (let [ssh-key (:name (get-ssh-keys api-key))]
    (post! (url-with-path "projects" project-id "devices" "batch")
           {:body {:batches
                   [{:hostname hostname
                     :plan plan
                     :operating_system operating_system
                     :quantity quantity
                     :ssh_keys [ssh-key]}]}
            :apikey key})))

;; Example Invitation
;;(create-organizations-invitation orgid "foo@bar.com" "owner" projectid authToken)

(create-device projectid "test.dell.com" "baremetal_1e" "ubuntu_18_04" 1 authToken)

;; List invitations
;;(pprint (get-organizations-invitations orgid authToken))

;; Note authToken is private


;; Device


;;https://api.packet.net/projects/e40d191d-4394-477c-b47d-2f08cc26a98a/devices/batch?include=plan,ip_addresses,facility,ssh_keys,project,volumes,volumes.attachments,volumes.facility,volumes.snapshots,volumes.snapshot_policies,virtual_networks&exclude=project_lite&token=jD7qVsmzNyUefh7jXbapXR2PhRjLNKXt


;;{"batches":[{"hostname":"test.delltest.com","plan":"baremetal_1e","operating_system":"ubuntu_18_04","facility":"ewr1","userdata":"","spot_instance":false,"spot_price_max":"","quantity":1,"public_ipv4_subnet_size":"28","private_ipv4_subnet_size":"28","user_ssh_keys":["a9375e9f-dea7-46d2-a80e-866c2c1ea336"]}]}


