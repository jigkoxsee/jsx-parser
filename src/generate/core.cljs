(ns generate.core
  (:require ["babylon" :as babel]
            [generate.utils :as utils]
            [clojure.string :as string]))

(defn trim [code]
  (-> code
      (string/split-lines)
      (->>
        (map string/trim)
        (string/join "\n"))))


(defn parse [code]
  (-> code
      trim
      (babel/parse (clj->js {:sourceType "module" :plugins [:jsx]}))
      (utils/obj->clj true)
      (get-in [:program :body 0 :expression])))


(defn is-not-capital-case [val]
  (let [f (first val)
        up-f (string/lower-case f)]
    (= f up-f)))

(defn get-tag [name]
  (if (is-not-capital-case name)
    (keyword name)
    (symbol name)))


(def JSLiteral "Literal")
(def JSIdentifier "Identifier")
(def JSAssignmentExpression "AssignmentExpression")
(def JSLogicalExpression "LogicalExpression")
(def JSConditionalExpression "ConditionalExpression")
(def JSBinaryExpression "BinaryExpression")
(def JSMemberExpression "MemberExpression")
(def JSObjectExpression "ObjectExpression")
(def JSExpressionStatement "ExpressionStatement")
(def JSThisExpression "ThisExpression")
(def JSArrowFunctionExpression "ArrowFunctionExpression")
(def JSBlockStatement "BlockStatement")
(def JSStringLiteral "StringLiteral")
(def JSNumericLiteral "NumericLiteral")
(def JSUnaryExpression "UnaryExpression")

;;
(def BooleanAttribute "BooleanAttribute")

;; --- JSX ---
;; Element
(def JSXElement "JSXElement")
(def JSXFragment "JSXFragment")
;(def JSXSelfClosingElement "JSXSelfClosingElement")
(def JSXOpeningElement "JSXOpeningElement")
(def JSXClosingElement "JSXClosingElement")
(def JSXElementName  "JSXElementName")
(def JSXIdentifier "JSXIdentifier")
;(def JSXNamespacedName "JSXNamespacedName")
(def JSXMemberExpression "JSXMemberExpression")

;; Attr
;(def JSXAttributes "JSXAttributes")
(def JSXSpreadAttribute "JSXSpreadAttribute")
(def JSXAttribute "JSXAttribute")
(def JSXAttributeName "JSXAttributeName")
(def JSXAttributeInitializer "JSXAttributeInitializer")
(def JSXAttributeValue "JSXAttributeValue")
;(def JSXDoubleStringCharacters "JSXDoubleStringCharacters")
;(def JSXDoubleStringCharacter "JSXDoubleStringCharacter")
;(def JSXSingleStringCharacters "JSXSingleStringCharacters")
;(def JSXSingleStringCharacter "JSXSingleStringCharacter")
;; Children
;(def JSXChildren "")
;(def JSXChild "")
(def JSXText "JSXText")
;(def JSXTextCharacter "")
;(def JSXChildExpression "")
(def JSXExpressionContainer "JSXExpressionContainer")



(defn get-operator-symbol [operator]
  (case operator
    "==" (symbol "=")
    "!=" (symbol "not=")
    "&&" (symbol "and")
    "||" (symbol "or")
    (symbol operator)))


(defn to-hiccup [ast]
  (let [is-vector (vector? ast)
        is-map (map? ast)
        to-attr (fn [attr]
                  (let [-key (get-in attr [:name :name])
                        attr-key (keyword (case -key
                                            "className" "class"
                                            -key))
                        val (get-in attr [:value :value])
                        val-type (get-in attr [:value :type] BooleanAttribute)]
                    (condp = val-type
                      BooleanAttribute [attr-key true]
                      [attr-key (-> attr :value to-hiccup)])))

        to-attrs (fn [attrs] (into {} (map to-attr attrs)))]
    (cond
      is-vector
      (let [res (filter #(not= "" %) (map to-hiccup ast))
            cnt (count res)]
        (if (= cnt 1)
          (first res)
          res))
      is-map
      (condp = (get ast :type)
        JSXElement
        (let [ name (-> (get-in ast [:openingElement :name])
                       to-hiccup)
              attrs (-> (get-in ast [:openingElement :attributes])
                        to-attrs)
              children (get ast :children)]
          (cond
            (and (empty? attrs))
            (into [] (remove nil? (into [name] (to-hiccup children))))

            (and (not (empty? attrs)))
            (into [] (remove nil? (into [name attrs] (to-hiccup children))))))
            
        nil
        nil

        JSXIdentifier
        (let [name (-> ast :name)]
          (get-tag name))

        JSXMemberExpression
        (let [left (-> ast :object to-hiccup)
              right (-> ast :property to-hiccup)]
          (get-tag (str left "." right)))

        JSXText
        (let [text (-> ast :value string/trim)]
          (if (= text "")
            nil
            (list text)))

        JSXExpressionContainer
        (let [node (get ast :expression)]
          (to-hiccup node))


        ;; JavaScript
        JSExpressionStatement
        (-> ast :expression to-hiccup)

        JSThisExpression
        (symbol "this")

        JSIdentifier
        (let [val (get ast :name)
              type (get ast :internal-type)]
          (case type
            :Key
            (keyword val)
            (symbol val)))

        JSLiteral
        (-> ast :value)

        JSStringLiteral
        (-> ast :value)

        JSNumericLiteral
        (-> ast :value)

        JSLogicalExpression
        (let [operator (-> ast :operator get-operator-symbol)
              left (-> ast :left to-hiccup)
              right (-> ast :right to-hiccup)]
          (list operator left right))

        JSConditionalExpression
        (let [test (-> ast :test to-hiccup)
              consequent (-> ast :consequent to-hiccup)
              alternate (-> ast :alternate to-hiccup)]
          (list (symbol "if")
                test
                consequent
                alternate))

        JSBinaryExpression
        (let [operator (get-operator-symbol (get ast :operator))
              left (-> ast :left to-hiccup)
              right (-> ast :right to-hiccup)]
          (list operator left right))

        JSUnaryExpression
        (let [operator (-> ast :operator)
              arg (-> ast :argument to-hiccup)]
          (list (symbol operator) arg))

        JSObjectExpression
        (let [props (get ast :properties)]
          (into {}
                (for [p props]
                  (let [k (-> p :key (assoc :internal-type :Key) to-hiccup)
                        v (-> p :value to-hiccup)]
                    [k v]))))

        JSMemberExpression
        (let [left (-> ast :object to-hiccup)
              right (-> ast :property to-hiccup)]
          (symbol (str left "." right)))

        JSArrowFunctionExpression
        (list (symbol "fn")
              (into [] (map #(to-hiccup %) (:params ast)))
              (-> ast :body to-hiccup))

        JSBlockStatement
        (list (symbol "do") "something here")

        [:unknown-type (get ast :type)])
      :else :unknown)))


