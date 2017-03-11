Version 1.2.2 (unreleased)
--------------
* Fixed a bug where the newer state messages would fail if the game hadn't
    already registered Vec3d as a serializable class.
* Fixed NetworkStateListener to properly pay attention to zone extents.
* Fixed EtherealHost to pass the specified client zone extents on to the 
    NetworkStateListener.
* Added additional trace logging to NetworkStateListener.
* Upgraded to sim-math 1.1.1   


Version 1.2.1
--------------
* Added EtherealHost.setStateCollectionInterval() to configure how often
    state is retrieved from the ZoneManager and sent to the clients.
* Added TimeSource.set/getOffset() to make it easier for clients to configure
    how far in history they'd like time to represent.  Defaults to -100 ms.
* Expanded ZoneManager's javadoc.
* Made StateCollector's idle sleep time configurable and exposed it as
    an EtherealHost property.
* Flipped the StateCollector's update loop to sleep when idle instead of
    only after a valid state collection was made.
* Added a ConnectionStats object to the NetworkStateListener that currently collects
    the average ping time, percentage of missed ACKs, and the average message size.
* Modified the StateReceiver to add message size stats to a Statistics tracker.  This
    let's clients see the average and total message sizes.
* Modified the build.gradle to replace the JME version with a specific 
    version instead of letting it float.  I think alpha4 is generally
    the minimum accepted 3.1 version at this point. 
    Did the same for all of the floating version references.
    

Version 1.1.1
--------------
* Added an EtherealHost.setConnectionObject() to make it easier
    to set the connections object ID and initial position.  The older
    way required some tighter coupling to Ethereal internals.
    Old way to start hosting on a connection:
        getService(EtherealHost.class).startHostingOnConnection(conn);
        getService(EtherealHost.class).getStateListener(conn).setSelf(ship.getId(), new Vec3d());
    New way:
        getService(EtherealHost.class).startHostingOnConnection(conn);
        getService(EtherealHost.class).setConnectionObject(conn, ship.getId(), new Vec3d())
* Improved an exception message on LocalZoneIndex's out of bounds checking.    
* Improved an exception message in FrameStat's split() regarding a failure to split.  The message
    now includes the caller specified limit so we can see if it's even a sane value in these
    error cases.
* Attempted fix for a message split bug (untested).  Based on the logic, there were some
    book-keeping items not properly updated which could lead to exactly the error cases
    reported.  Hopefully fixing those calculations fixes the problem but I've also left
    a ton of comments in place.
* Added a StateWriter.get/setMaxMessageSize() method(s) for controlling the MTU size used
    to calculate when messages are split.  So even if the above fix doesn't work then callers
    can effectively turn off the split behavior by setting a huge max message size.     
* Exposed get/setMaxMessageSize() at the NetworkStateListener level so that application code
    might set this value on its own, or even dynamically, based on application-specific
    requirements.


Version 1.0.1
--------------
* Initial public release with maven artifacts
