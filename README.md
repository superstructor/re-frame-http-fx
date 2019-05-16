[![GitHub license](https://img.shields.io/github/license/Day8/re-frame-http-fx.svg)](license.txt)
[![Circle CI](https://circleci.com/gh/Day8/re-frame-http-fx/tree/master.svg?style=shield&circle-token=:circle-ci-badge-token)](https://circleci.com/gh/Day8/re-frame-http-fx/tree/master)

# HTTP Effects Handler for re-frame

This re-frame library contains a HTTP 
[Effect Handler](https://github.com/Day8/re-frame/tree/develop/docs).

Keyed `:http/req`, it wraps the `goog.net.Xhrio` API of
[cljs-ajax](https://github.com/JulianBirch/cljs-ajax).

> **BREAKING CHANGES**: As of version `0.2.0` there are significant breaking
> changes to the effect's keys, features and return values. Versions `0.1.x`
> and older are documented in [docs/README-0.1.md](docs/README-0.1.md).

> **IMPORTANT**: This effect handler depends entirely on the API of
> [cljs-ajax](https://github.com/JulianBirch/cljs-ajax). Make sure you are
> familiar with the API for `cljs-ajax`, and especially with 
> [`ajax-request`](https://github.com/JulianBirch/cljs-ajax#ajax-request).

## Quick Start Guide

### Step 1. Add Dependency

Add the following project dependency: <br>
[![Clojars Project](https://img.shields.io/clojars/v/day8.re-frame/http-fx.svg)](https://clojars.org/day8.re-frame/http-fx)

Requires re-frame >= `0.8.0`.

### Step 2. Registration and Use

In the namespace where you register your event handlers, prehaps called
`events.cljs`, you have two things to do.

**First**, add this `require` to the `ns`:
```clojure
(ns app.core
  (:require
    ...
    [day8.re-frame.http-fx]    ;; <-- add this
    ...))
```

Because we never subsequently use this `require`, it appears redundant. However
its existence will cause the `:http/req` effect handler to self-register with
re-frame, which is important to everything that follows.

**Second**, write an event handler which uses this effect:
```clojure
(ns app.events)

(reg-event-fx                      ;; note the trailing -fx
  ::github-day8-req                ;; usage: (dispatch [::app.events/github-day8-req])
  (fn [{:keys [db]} _]             ;; the first param will be "world"
    {:db       (assoc db :show-twirly true)
     :http/req {:method     :get
                :uri        "https://api.github.com/orgs/day8"
                :timeout    8000
                :on-success [::github-day8-success]
                :on-failure [::github-day8-failure]}}))
```

Look at the `:http/req` line above. This library defines the "effects handler"
which implements `:http/req`.

The supplied value should be an options map as defined by the simple interface
`ajax-request` [see: api docs](https://github.com/JulianBirch/cljs-ajax#ajax-request).
This library does provide some defaults for `:format` and `:response-format`
detailed further down. Otherwise except for `:on-success` and `:on-failure` all
options are as supported by `ajax-request` as it is a thin wrapper over it.

There is an example of a POST request. Note that `:format` is defaulted to JSON
unless you set it to something else as detailed further down.

```clojure
(re-event-fx
  ::http-post
  (fn [_world [_ val]]
    {:http/req {:method  :post
                :uri     "https://httpbin.org/post"
                :params  data
                :timeout 5000
                :on-success [::http-post-success]
                :on-failure [::http-post-failure]}}))
```

Don't provide:

    :api     - the effects handler explicitly uses xhrio so it will be ignored.
    :handler - we substitute this with one that dispatches `:on-success` or
               `:on-failure` events.
               
You can also pass a list or vector of these options maps where multiple HTTP
requests are required.

To make **multiple requests**, supply a vector of options maps:
```clojure
{:http/req [ {...}
             {...} ]}
```

### Step 3a. Handling `:on-success`

Provide normal re-frame handlers for `:on-success` and `:on-failure`. Your event
handlers will get the result as the last argument of their event vector. Here is
an example written as another effect handler to put the result into db.

```clojure
(reg-event-db
  ::http-get-success
  (fn [db [_ result]]
    (assoc db ::http-get-success result)))
```

#### `:on-success` with a parsable body

If the network connection to the server is successful, the server response has a
success status code and the body of the response is parsable then `result` will
be a map like:

```clojure
{:uri           "/example"
 :last-method   "POST"
 :status        201
 :status-text   "Created"
 :body          {:message "Hello!"}
 :debug-message "No Error"
 :headers {:location                     "/example/123"
           :date                         "Thu, 16 May 2019 01:14:50 GMT"
           :cache-control                "no-cache"
           :server                       "http-kit"
           :access-control-allow-origin  "http://localhost:3449"
           :content-length               "26"
           :access-control-allow-methods "DELETE, GET, POST, PUT"}}
```

#### `:on-success` with an unparsable body

If the network connection to the server is successful, the server response has a
success status code but the body of the response has a parse error then `result`
will be a map like:

```clojure
{:uri           "/example"
 :last-method   "POST"
 :status        201
 :status-text   "Created"
 :failure       :parse
 :parse-error   {:failure       :parse
                 :status-text   "Unexpected token H i … been JSON keywordize"
                 :original-text "Hello!"}
 :debug-message "No Error"
 :headers       {:location                     "/example/123"
                 :date                         "Thu, 16 May 2019 01:14:50 GMT"
                 :cache-control                "no-cache"
                 :server                       "http-kit"
                 :access-control-allow-origin  "http://localhost:3449"
                 :content-length               "26"
                 :access-control-allow-methods "DELETE, GET, POST, PUT"}}
```

### Step 3b. Handling `:on-failure`

Much the same as handling `:on-success` except it will be dispatched a result
when the network connection to the server fails or the server responds with a
failure status code.

```clojure
(reg-event-db
  ::http-get-failure
  (fn [db [_ result]]
    (assoc db ::http-get-failure result)))
```

#### `:on-failure` `:status` 0 - Network Connection Failure

In some cases, if the network connection itself is unsuccessful, it is possible
to get a status code of `0`. For example:

- cross-site scripting whereby access is denied; or
- requesting a URI that is unreachable (typo, DNS issues, invalid hostname etc); or
- request is interrupted after being sent (browser refresh or navigates away from the page); or
- request is otherwise intercepted (check your ad blocker).

In this case, `result` will be something like:

```clojure
{:uri             "http://i-do-not-exist/example"
 :last-method     "POST"
 :status          0
 :status-text     ""
 :failure         :failed
 :debug-message   "Http response at 400 or 500 level"
 :last-error      " [0]"
 :last-error-code 6}
```

#### `:on-failure` `:status` -1 - Timeout

If the time for the server to respond exceeds `:timeout` `result` will be a map
something like:

```clojure
{:uri             "/take-your-time"
 :last-method     "POST"
 :status          -1
 :status-text     ""
 :failure         :timeout
 :debug-message   "Request timed out"
 :last-error      "Timed out after 500ms, aborting"
 :last-error-code 8}
```

### Tip

If you need additional arguments or identifying tokens in your handler, then
include them in your `:on-success` and `:on-failure` event vector in Step 3. 

For example ... 

```cljs
(re-frame/reg-event-fx
  ::http-post
  (fn [_ [_ val]]
    {:http-xhrio {:method          :post
                  ...
                  :on-success      [::success-post-result 42 "other"]
                  :on-failure      [::failure-post-result :something :else]}}))
```

Notice the way that additional values are encoded into the success and failure 
event vectors. 

These event vectors will be dispatched (`result` is `conj`-ed to the end) making
all encoded values AND the `result` available to the handlers. 
