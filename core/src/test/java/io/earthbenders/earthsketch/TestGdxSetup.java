package io.earthbenders.earthsketch;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TestGdxSetup {

    private static boolean initialized = false;

    public static synchronized void init() {

        if (initialized) {
            return;
        }

        CountDownLatch ready = new CountDownLatch(1);

        Thread appThread = new Thread(() -> {

            Lwjgl3ApplicationConfiguration config =
                new Lwjgl3ApplicationConfiguration();

            config.setInitialVisible(false);
            config.disableAudio(true);

            new Lwjgl3Application(
                new ApplicationAdapter() {

                    @Override
                    public void create() {
                        ready.countDown();
                    }
                },
                config
            );
        });

        appThread.setDaemon(true);
        appThread.start();

        try {
            ready.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(
                "Interrupted while waiting for LibGDX",
                e
            );
        }

        initialized = true;
    }

    public static void runOnGdxThread(Runnable testCode) {

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure =
            new AtomicReference<>();

        Gdx.app.postRunnable(() -> {

            try {
                testCode.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                finished.countDown();
            }
        });

        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(
                "Interrupted while waiting for LibGDX test",
                e
            );
        }

        if (failure.get() != null) {

            Throwable cause = failure.get();

            if (cause instanceof AssertionError) {
                throw (AssertionError) cause;
            }

            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }

            throw new RuntimeException(cause);
        }
    }
}