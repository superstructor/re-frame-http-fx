(ns day8.re-frame.http-fx
  (:require
    [goog.net.ErrorCode :as errors]
    [re-frame.core :refer [reg-fx dispatch console]]
    [ajax.core :as ajax]))

(defn ajax-xhrio-handler-v2
  "ajax-request only provides a single handler for success and errors"
  [on-success on-failure xhrio [success? result]]
  ; see http://docs.closure-library.googlecode.com/git/class_goog_net_XhrIo.html
  (let [headers (js->clj (.getResponseHeaders xhrio) :keywordize-keys true)

        {:keys [failure response]} result

        details (cond->>
                  {:uri           (.getLastUri xhrio)
                   :last-method   (.-lastMethod_ xhrio)
                   :headers       headers
                   :status        (.getStatus xhrio)
                   :status-text   (.getStatusText xhrio)
                   :debug-message (-> xhrio .getLastErrorCode
                                      (errors/getDebugMessage))}

                  success?
                  ;; Successful response with a parsable body.
                  (merge {:response result})

                  (and (not success?)
                       (not (nil? response)))
                  ;; Failure response with a parsable body.
                  (merge {:response response})

                  (and (not (nil? failure))
                       (not (= :parse failure)))
                  ;; Failure response with a failure reason.
                  (merge {:failure failure
                          :last-error (.getLastError xhrio)
                          :last-error-code (.getLastErrorCode xhrio)})

                  (= :parse failure)
                  ;; Successful response with a body parse error.
                  ;; WARNING The map returned for this case is pretty broken.
                  ;; For example, the :status-text contains the :parse-error
                  ;; when it should contain the actual :status-text! Thus we
                  ;; treat this case with some special care.
                  (merge {:failure     :parse
                          :parse-error {:failure       :parse
                                        :status-text   (:status-text result)
                                        :original-text (.getResponseText xhrio)}})


                  (contains? result :parse-error)
                  ;; Failure response with a body parse error.
                  (merge
                    {:parse-error (select-keys (:parse-error result)
                                               [:failure :status-text :original-text])}))]
    (if success?
      (on-success details)
      (on-failure details))))

(defn request->xhrio-options-v2
  [{:as   request
    :keys [on-success on-failure]
    :or   {on-success      [:http-no-on-success]
           on-failure      [:http-no-on-failure]}}]
  ; wrap events in cljs-ajax callback
  (let [api (new js/goog.net.XhrIo)]
    (-> request
        (assoc
          :api     api
          :handler (partial ajax-xhrio-handler-v2
                            #(dispatch (conj on-success %))
                            #(dispatch (conj on-failure %))
                            api))
        (dissoc :on-success :on-failure))))

(defn http-effect-v2
  [request]
  (let [seq-request-maps (if (sequential? request) request [request])]
    (doseq [request seq-request-maps]
      (-> request request->xhrio-options-v2 ajax/ajax-request))))

(reg-fx :http/req http-effect-v2)


;; I provide the :http-xhrio effect handler leveraging cljs-ajax lib
;; see API docs https://github.com/JulianBirch/cljs-ajax
;; Note we use the ajax-request.
;;
;; Deviation from cljs-ajax options in request
;; :handler       - not supported, see :on-success and :on-failure
;; :on-success    - event vector dispatched with result
;; :on-failure    - event vector dispatched with result
;;
;; NOTE: if you need tokens or other values for your handlers,
;;       provide them in the on-success and on-failure event e.g.
;;       [:success-event "my-token"] your handler will get event-v
;;       [:success-event "my-token" result]


(defn ajax-xhrio-handler-v1
  "ajax-request only provides a single handler for success and errors"
  [on-success on-failure xhrio [success? response]]
  ; see http://docs.closure-library.googlecode.com/git/class_goog_net_XhrIo.html
  (if success?
    (on-success response)
    (let [details (merge
                    {:uri             (.getLastUri xhrio)
                     :last-method     (.-lastMethod_ xhrio)
                     :last-error      (.getLastError xhrio)
                     :last-error-code (.getLastErrorCode xhrio)
                     :debug-message   (-> xhrio .getLastErrorCode (errors/getDebugMessage))}
                    response)]
      (on-failure details))))

(defn request->xhrio-options-v1
  [{:as   request
    :keys [on-success on-failure]
    :or   {on-success      [:http-no-on-success]
           on-failure      [:http-no-on-failure]}}]
  ; wrap events in cljs-ajax callback
  (let [api (new js/goog.net.XhrIo)]
    (-> request
        (assoc
          :api     api
          :handler (partial ajax-xhrio-handler-v1
                            #(dispatch (conj on-success %))
                            #(dispatch (conj on-failure %))
                            api))
        (dissoc :on-success :on-failure))))

(defn http-effect-v1
  [request]
  (console :warn "re-frame-http-fx: \":http-xhrio\" fx is deprecated. Use \":http/req\".")
  (let [seq-request-maps (if (sequential? request) request [request])]
    (doseq [request seq-request-maps]
      (-> request request->xhrio-options-v1 ajax/ajax-request))))

(reg-fx :http-xhrio http-effect-v1)
