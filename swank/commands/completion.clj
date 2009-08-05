(remove-ns 'swank.commands.completion)
(ns swank.commands.completion
  (:use (swank util core commands)
        (swank.util string clojure java)))

(defn potential-ns
  "Returns a list of potential namespace completions for a given
   namespace"
  ([] (potential-ns *ns*))
  ([ns]
     (for [ns-sym (concat (keys (ns-aliases (ns-name ns)))
                          (map ns-name (all-ns)))]
       (name ns-sym))))

(defn potential-var-public
  "Returns a list of potential public var name completions for a
   given namespace"
  ([] (potential-var-public *ns*))
  ([ns]
     (for [var-sym (keys (ns-publics ns))]
       (name var-sym))))

(defn potential-var
  "Returns a list of all potential var name completions for a given
   namespace"
  ([] (potential-var *ns*))
  ([ns]
     (for [[key v] (ns-map ns)
           :when (var? v)]
       (name key))))

(defn potential-classes
  "Returns a list of potential class name completions for a given
   namespace"
  ([] (potential-classes *ns*))
  ([ns]
     (for [class-sym (keys (ns-imports ns))]
       (name class-sym))))

(defn potential-dot
  "Returns a list of potential dot method name completions for a given
   namespace"
  ([] (potential-dot *ns*))
  ([ns]
     (map #(str "." %) (set (map method-name (mapcat instance-methods (vals (ns-imports ns))))))))

(defn potential-static
  "Returns a list of potential static methods for a given namespace"
  ([#^Class class]
     (map method-name (static-methods class))))

(defn resolve-class
  "Attempts to resolve a symbol into a java Class. Returns nil on
   failure."
  ([sym]
     (try
      (let [res (resolve sym)]
        (when (class? res)
          res))
      (catch Throwable t
        nil))))

(defn potential [symbol-ns ns]
  (if symbol-ns
    (map #(str symbol-ns "/" %)
         (if-let [class (resolve-class symbol-ns)]
           (potential-static class)
           (potential-var-public symbol-ns)))
    (concat (potential-var ns)
            (when-not symbol-ns
              (potential-ns))
            (potential-classes ns)
            (potential-dot ns))))

(defn- maybe-alias [sym ns]
  (or (resolve-ns sym (maybe-ns ns))
      (maybe-ns ns)))

(defslimefn simple-completions [symbol-string package]
  (try
   (let [[sym-ns sym-name] (symbol-name-parts symbol-string)
         potential-names   (potential (when sym-ns (symbol sym-ns)) (ns-name (maybe-ns package)))
         matches           (seq (sort (filter #(.startsWith #^String % symbol-string) potential-names)))]
     (list matches
           (if matches
             (reduce largest-common-prefix matches)
             symbol-string)))
   (catch java.lang.Throwable t
     (list nil symbol-string))))