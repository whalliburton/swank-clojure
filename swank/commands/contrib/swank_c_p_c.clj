(ns swank.commands.contrib.swank-c-p-c
  (:use (swank util core commands)
        (swank.util string clojure)))

(defn- unacronym
  "Interposes delimiter between each character of string."
  ([delimiter #^String string]
     (apply str (interpose delimiter string)))
  {:tag String})

;;; Ported from slime's swank.
(defn- compound-prefix-match
  "Takes a `prefix' and a `target' string and which returns position
  of last character in `target' which matches `prefix' if `prefix' is
  a compound-prefix of `target', and otherwise nil.

  Viewing each of `prefix' and `target' as a series of substrings
  delimited by `delimeter', if each substring of `prefix' is a prefix
  of the corresponding substring in `target' then we call `prefix' a
  compound-prefix of `target'."
  ([delimeter prefix target] (compound-prefix-match delimeter prefix target nil))
  ([delimeter #^String prefix #^String target no-acronyms?]
     (if (= "" prefix)
       0
       (if (not no-acronyms?)
         (or (compound-prefix-match delimeter prefix target true)
             (compound-prefix-match delimeter
                                    (unacronym delimeter prefix)
                                    target
                                    true))
         (loop [prefix prefix
                tpos 0]
           (let [ch (first prefix)
                 new-tpos (if (= ch delimeter)
                            (position delimeter target tpos)
                            tpos)]
             (when (and tpos
                        (< tpos (.length target))
                        (if (not= tpos new-tpos)
                          new-tpos
                          (= ch (.charAt target tpos))))
               (if-let [newprefix (rest prefix)]
                 (recur newprefix
                        (inc new-tpos))
                 new-tpos))))))))

(defn- ns-exists?
  "Given an string its-name, returns either an ns if a like named ns
  exists, or nil. If its-name is nil, returns nil."
  [#^String its-name]
  (and its-name
       (find-ns (symbol its-name))))

(defn- completion-list
  "Returns a list of vars or nses (depending on value of of-what:
  either :var or :ns) that are possible compound completions of sym,
  given that maybe-ns is nil or an ns in which to search of vars if
  of-what is :var, and current-ns is the ns of the context of the
  completion or nil.

  The compound completion delimeter is `.' for namespaces and `-' for
  symbols."
  [of-what, #^String sym, & [maybe-ns, current-ns]]
  (cond
   (= :ns of-what)
   (filter (partial compound-prefix-match \. sym)
           (map (comp name ns-name)     ;name of ns as String
                (all-ns)))
   (= :var of-what)
   (map
    (if maybe-ns
      (partial str maybe-ns \/)
      identity)
    (filter
     (partial compound-prefix-match \- sym)
     (map
      (comp name :name meta)            ;name of var as String
      (filter var?
              (vals (if (or (not maybe-ns)
                            (= maybe-ns current-ns))
                      ;; In current namespace, complete to all vars,
                      ;; in other namespaces -- only to public vars.
                      (ns-map current-ns)
                      (ns-publics maybe-ns)))))))))

(defn- compound-complete
  "Returns a list of possible completions of sym in cur-ns."
  [#^String sym,
   #^String cur-ns]
  (let [[sym-ns sym-name] (symbol-name-parts sym)
        sym-ns (ns-exists? sym-ns)
        cur-ns (ns-exists? cur-ns)]
    (if sym-ns
      (completion-list :var sym-name sym-ns cur-ns)
      (concat
       (completion-list :var sym-name nil cur-ns)
       (map #(str % \/)
            (completion-list :ns sym-name))))))

(defslimefn completions [string package]
  (let [matches (sort (compound-complete string package))
        longest-comp (if matches
                       (reduce largest-common-prefix matches)
                       string)]
    (list matches longest-comp)))
