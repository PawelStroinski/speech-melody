(defproject speech-melody "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [overtone "0.9.1"]
                 [clj-http "1.1.1"]
                 [incanter "1.5.6"]
                 [leipzig "0.9.0"]
                 ]
  :main ^:skip-aot speech-melody.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :java-source-paths ["src/speech_melody/java"]
  :resource-paths ["resources/*" "resources/vorbis-java-1.0.0-beta.jar"]
  :repl-options {:timeout 3600000})
