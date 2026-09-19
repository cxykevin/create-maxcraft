package dev.maxcraft.content.logistics;

/**
 * Marks the package currently being unwrapped as one that came from a Large Packager.
 *
 * <p>Create's unpacking handlers are given the package's <em>contents</em>, never the package itself, so there is
 * nothing in their signature to tell a large package from an ordinary one. The unpack happens synchronously inside
 * {@code PackagerBlockEntity#unwrapBox}, which does have the package, so a scope around that call is enough.
 */
public final class LargePackageContext {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private LargePackageContext() {
    }

    public static void set(boolean active) {
        ACTIVE.set(active);
    }

    public static void clear() {
        ACTIVE.remove();
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }
}
