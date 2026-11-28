package cn.tolim.pudans.iam.appkernel.core.context;


public final class ApplicationContextHolder {
    private static final ThreadLocal<ApplicationContext> HOLDER = new ThreadLocal<>();

    private ApplicationContextHolder() {
    }

    // Returns the ApplicationContext bound to the current thread.
    public static ApplicationContext getApplicationContext() {
        return HOLDER.get();
    }

    // Bind the given ApplicationContext to the current thread.
    public static void setContext(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            resetContext();
        } else {
            HOLDER.set(applicationContext);
        }
    }

    // Reset the ApplicationContext bound to the current thread.
    public static void resetContext() {
        HOLDER.remove();
    }

}
