(ns contajners.jvm-runtime
  (:require
   [pem-reader.core :as pem]
   [unixsocket-http.core :as http])
  (:import
   [java.security KeyPair]
   [java.security.cert X509Certificate]
   [okhttp3 OkHttpClient$Builder]
   [okhttp3.tls HandshakeCertificates$Builder HeldCertificate]))

(defn- read-cert
  "Loads a PEM file from a given path and returns the certificate from it."
  [path]
  (-> path
      (pem/read)
      (:certificate)))

(defn- make-builder-fn
  "Creates a builder fn to load the certs for mTLS.

  This is expected by unixsocket-http underlying mechanism."
  [{:keys [ca cert key]}]
  (let [{:keys [public-key private-key]} (pem/read key)
        key-pair (KeyPair. public-key private-key)
        held-cert (HeldCertificate. key-pair (read-cert cert))
        handshake-certs (-> (HandshakeCertificates$Builder.)
                            (.addTrustedCertificate (read-cert ca))
                            (.heldCertificate held-cert (into-array X509Certificate []))
                            (.build))]
    (fn [^OkHttpClient$Builder builder]
      (.sslSocketFactory builder
                         (.sslSocketFactory handshake-certs)
                         (.trustManager handshake-certs)))))

(defn client
  [uri opts]
  (http/client uri
               (assoc opts
                      :mode :recreate
                      :builder-fn (if-let [mtls (:mtls opts)]
                                    (make-builder-fn mtls)
                                    identity))))

(defn request
  "Internal fn to perform the request."
  [{:keys [client method path headers query-params body as throw-exceptions throw-entire-message]}]
  (-> {:client client
       :method method
       :url path
       :headers headers
       :query-params query-params
       :body body
       :as (if (or (= :data as)
                   (nil? as))
             :string
             as)
       :throw-exceptions throw-exceptions
       :throw-entire-message? throw-entire-message}
      (http/request)
      (:body)))
