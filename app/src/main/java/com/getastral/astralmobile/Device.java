package com.getastral.astralmobile;

public class Device {
    //private variables
    String _uuid;
    String _name;
    String _appliance_type;
    String _appliance_make;
    String _appliance_model;

    public String getUuid() {
        return _uuid;
    }

    public void setUuid(String _uuid) {
        this._uuid = _uuid;
    }

    public String getName() {
        return _name;
    }

    public void setName(String _name) {
        this._name = _name;
    }

    public String getApplianceType() {
        return _appliance_type;
    }

    public void setApplianceType(String _appliance_type) {
        this._appliance_type = _appliance_type;
    }

    public String getApplianceMake() {
        return _appliance_make;
    }

    public void setApplianceMake(String _appliance_make) {
        this._appliance_make = _appliance_make;
    }

    public String getApplianceModel() {
        return _appliance_model;
    }

    public void setApplianceModel(String _appliance_model) {
        this._appliance_model = _appliance_model;
    }

    // Empty constructor
    public Device(){

    }
    // constructor
    public Device(String uuid, String name, String appliance_type, String appliance_make, String appliance_model){
        this._uuid = uuid;
        this._name = name;
        this._appliance_type = appliance_type;
        this._appliance_make = appliance_make;
        this._appliance_model = appliance_model;
    }

    // constructor
    public Device(String name, String appliance_type, String appliance_make, String appliance_model){
        this._name = name;
        this._appliance_type = appliance_type;
        this._appliance_make = appliance_make;
        this._appliance_model = appliance_model;
    }
}
