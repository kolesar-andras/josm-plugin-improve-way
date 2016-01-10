ImproveWay
==========

[JOSM](https://josm.openstreetmap.de/) plugin extending [Improve Way Accuracy](https://josm.openstreetmap.de/wiki/Help/Action/ImproveWayAccuracy) mode with helpers to place nodes at accurate distances and angles.

Installation
------------

Download JAR file from <http://kolesar.turistautak.hu/osm/josm/plugins/ImproveWay.jar> and place it to plugins directory `~/.josm/plugins/`. Go to preferences (F12) and make three changes here:

* enable plugin
* enable expert mode (some features are hidden from beginner users)
* disable keyboard shortcut W for builtin tool named Mode: Improve Way Accuracy (W): unset "use default" setting and set "disable" (if you can't find this entry, create a new empty layer to initialize tools)

Keyboard shortcut change needs restart, after you will see a new icon on editing toolbar, similar to Improve Way Accuracy mode but greyscale.

Usage
-----

Extended features appear when you press W second time.

* arcs representing turn angles at nodes
* node distances in meters
* node turn angles in degrees
* a perpendicular line halfway between the current node pair
* a circle where turn angle of moved/inserted node will be equal to neighbours

These objects follow mouse in real time. If you press Alt+Ctrl, mouse position is ignored, extended helper objects use original nodes. The same display is available any time long-pressing W, even when other mode is active.

Windows key (also called meta, super, mod4) locks new position to the center of equal angle circle.

![screenshot](http://kolesar.turistautak.hu/osm/josm/plugins/ImproveWay/screenshots/railway.png "screenshot of railway line")

Authors
-------

Original tool called "Improve Way Accuracy" was written by [Alexander Kachkaev](https://github.com/kachkaev). Extended features were added by [András Kolesár](https://github.com/kolesar-andras). Feedbacks are welcome.
