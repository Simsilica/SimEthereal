Version 1.8.1 (unreleased)
--------------
* Updated BitOutputStream and BitInputStream to be AutoCloseable
* Updated StateReceiving to only log the full stale state if trace
    logging is on.  Also changed to info because it's not really a sign
    of a big problem unless it happens a lot.


Version 1.8.0 (latest)
--------------
* fixed ObjectStateMessages to actually be UDP.
* fixed a bug in how the ping time was calculated by modifying
    ClientStateMessage.resetReceivedTime() to take a timestamp instead of
    getting it directly from System.nanoTime().
* Converted a println() in ZoneManager to a log.warn() related to exceeding
    history size watchdog threshold.
* Fixed a long-standing bug related to objects moving so far that none of
    their old zones are valid anymore.  This left the old StateListener
    stranded if the object was what gave the state listener its 'center'.
* Added StateListener.objectWarped() and the NetworkStateListener.objectWarped()
    implementation to fix the bug mentioned above.  This is a breaking change
    for any apps implementing their own StateListener.


Version 1.7.0
--------------
* fixed an ID comparison bug in some watchdog code
* Upgraded the build to use gradle 7.4.2
* Migrated publishing to maven central instead of jcenter


Version 1.6.0
--------------
* Added parent/child relationships between network shared objects.
* Fixed a bug in the assertion logic in BufferedHashSet's thread check.


Version 1.5.0
--------------
* Internally moved ZoneManager creation to the EtherealHost constructor
    so that it could be accessed prior to EtherealHost service initialization.
* Refactored ZoneManager to allow different internal ZoneRange implementations.
    Renamed the old internal ZoneRange to OctZoneRange to denote that it can
    track a maximum of eight zones per object (2x2x2).  This limited objects
    to never be bigger than a zone.
* Modified ZoneManager to have a new internal DynamicZoneRange that can
    support objects of virtually any size relative to zone size.  (Subject
    to positional bit resolution, etc.)
* Added a (hopefully temporary) ZoneManager.setSupportLargeObjects() that
    defaults to false.  When true this will use the new DynamicZoneRange.
    This defaults to false because the older (uglier) ZoneRange code has
    had a LOT more real world testing.  Note: the new code is actually way
    cleaner and more elegant.
* Modified the "received acks" watchdog in StateWriter to take message ID lag
    into account.  This should fix the cases where the exception would be
    thrown for cases where client ACKs are just lagging by a wide margin.
* Upgraded to SimMath 1.4.0 to get the new IntRangeSet for receved acks tracking.
* Converted the tracked ACK message ID sets to IntRangeSets for efficient storage
    and processing.  (During normal processing, ACK IDs will almost always be
    a single contiguous range so a good candidate for IntRangeSet: one entry)
* Fixed a bug in SentState message writing/receiving where more than 255 ACK
    IDs was causing overflow and randomly lost acks.  (Fixed by sending ranges
    instead of every individual ACK set.)  This also fixed a message size
    overflow issue.


Version 1.4.0
--------------
* Fixed zone ID calculation for non-uniform grids.  See PR #2.
* Modified ZoneManager to automatically send "no-change" updates for
    objects it is managing but didn't receive updates for.  See PR #5
    (Note: this could seem like a 'breaking change' to any apps relying
    on objects to auto-expire in this way... just know that doing so was
    leaving extra garbage around and so not really a solution.)
* Added a thread-safe BufferedHashSet for creating "one writer, many readers"
    fast thread safe hash sets.
* Added a double-buffered thread safe active IDs set to the NetworkStateListener.
* Added an error log message when updating an object with a bounds bigger than
    supported by the current grid settings.


Version 1.3.0
--------------
* Fixed a bug where the newer state messages would fail if the game hadn't
    already registered Vec3d as a serializable class.
* Fixed NetworkStateListener to properly pay attention to zone extents.
* Fixed EtherealHost to pass the specified client zone extents on to the
    NetworkStateListener.
* Added additional trace logging to NetworkStateListener.
* Fixed an issue where raw nanoTime() was being used to timestamp messages.
    Now applications can specify their own TimeSource on the server.
    See issue #4.
* Refactored the TimeSource interface to be just a time provider and added
    a SynchedTimeSource interface to provide the extra drift/offset methods
    that it previously had.  RemoteTimeSource now implements SynchedTimeSource.
* Upgraded to sim-math 1.2.0
* Set sourceCompatibility to 1.7


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
