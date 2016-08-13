
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
    

Version 1.0.1
--------------
* Initial public release with maven artifacts
