package py.com.sodep.mf.exchange.activation;

import py.com.sodep.mf.exchange.objects.device.MFDevice;

public class ActivationRequest {
    public MFDevice getDevice() {
        return device;
    }

    public ActivationRequest() {
    }

    public ActivationRequest(MFDevice device, String email) {
        this.device = device;
        this.email = email;
    }

    public void setDevice(MFDevice device) {
        this.device = device;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    private MFDevice device;
    private String email;
}
