(defproject box-vis "0.1-alpha"


  :description "Box-Vis: For Use with Options-Boy"

  :url "https://stonks.expert"

  :license {:name "GNU GPL v3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [trystan/delaunay-triangulation "1.0.1"]
                 [quil "2.5.0"]
                 [com.rpl/specter "1.1.3"]
                 [clj-http "3.10.0"]

                 [http-kit "2.5.1"]
                 [cheshire "5.9.0"]
                 ]

  :main box-vis.core )
