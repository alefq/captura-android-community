package py.com.sodep.mf.exchange.activation;

public class ActivationStatusResponse {
    private boolean active;

    public ActivationStatusResponse() {
    }

    public ActivationStatusResponse(boolean active) {
        this.active = active;
    }

    // Getters y setters
    public boolean isActive() {
        return active;

    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
