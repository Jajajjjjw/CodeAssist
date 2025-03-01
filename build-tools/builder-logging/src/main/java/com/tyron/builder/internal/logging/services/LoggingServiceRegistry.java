package com.tyron.builder.internal.logging.services;

import com.tyron.builder.internal.logging.events.OutputEventListener;
import com.tyron.builder.internal.logging.services.DefaultStyledTextOutputFactory;
import com.tyron.builder.internal.logging.text.StyledTextOutputFactory;
import com.tyron.builder.internal.reflect.service.DefaultServiceRegistry;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.internal.time.Clock;
import com.tyron.builder.internal.time.Time;
import com.tyron.builder.api.logging.LogLevel;
import com.tyron.builder.api.logging.configuration.ConsoleOutput;
import com.tyron.builder.internal.logging.LoggingManagerInternal;
import com.tyron.builder.internal.logging.config.LoggingSourceSystem;
import com.tyron.builder.internal.logging.config.LoggingSystemAdapter;
import com.tyron.builder.internal.logging.sink.OutputEventListenerManager;
import com.tyron.builder.internal.logging.sink.OutputEventRenderer;
import com.tyron.builder.internal.logging.slf4j.Slf4jLoggingConfigurer;
import com.tyron.builder.internal.logging.source.DefaultStdErrLoggingSystem;
import com.tyron.builder.internal.logging.source.DefaultStdOutLoggingSystem;
import com.tyron.builder.internal.logging.source.JavaUtilLoggingSystem;
import com.tyron.builder.internal.logging.source.NoOpLoggingSystem;

/**
 * A {@link ServiceRegistry} implementation that provides the logging services. To use this:
 *
 * <ol>
 * <li>Create an instance using one of the static factory methods below.</li>
 * <li>Create an instance of {@link LoggingManagerInternal}.</li>
 * <li>Configure the logging manager as appropriate.</li>
 * <li>Start the logging manager using {@link LoggingManagerInternal#start()}.</li>
 * <li>When finished, stop the logging manager using {@link LoggingManagerInternal#stop()}.</li>
 * </ol>
 */
public abstract class LoggingServiceRegistry extends DefaultServiceRegistry {

    public static final Object NO_OP = new Object() {
        OutputEventListener createOutputEventListener() {
            return OutputEventListener.NO_OP;
        }
    };


    private TextStreamOutputEventListener stdoutListener;

    protected final OutputEventRenderer renderer = makeOutputEventRenderer();
    protected final OutputEventListenerManager outputEventListenerManager = new OutputEventListenerManager(renderer);

    /**
     * Creates a set of logging services which are suitable to use globally in a process. In particular:
     *
     * <ul>
     * <li>Replaces System.out and System.err with implementations that route output through the logging system as per {@link LoggingManagerInternal#captureSystemSources()}.</li>
     * <li>Configures slf4j, log4j and java util logging to route log messages through the logging system.</li>
     * <li>Routes logging output to the original System.out and System.err as per {@link LoggingManagerInternal#attachSystemOutAndErr()}.</li>
     * <li>Sets log level to {@link LogLevel#LIFECYCLE}.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     *
     * <p>Allows dynamic and colored output to be written to the console. Use {@link LoggingManagerInternal#attachProcessConsole(ConsoleOutput)} to enable this.</p>
     */
    public static LoggingServiceRegistry newCommandLineProcessLogging() {
        CommandLineLogging loggingServices = new CommandLineLogging();
        LoggingManagerInternal
                rootLoggingManager = loggingServices.get(DefaultLoggingManagerFactory.class).getRoot();
        rootLoggingManager.captureSystemSources();
        rootLoggingManager.attachSystemOutAndErr();
        return loggingServices;
    }

    /**
     * Creates a set of logging services which are suitable to use embedded in another application. In particular:
     *
     * <ul>
     * <li>Configures slf4j and log4j to route log messages through the logging system.</li>
     * <li>Sets log level to {@link LogLevel#LIFECYCLE}.</li>
     * </ul>
     *
     * <p>Does not:</p>
     *
     * <ul>
     * <li>Replace System.out and System.err to capture output written to these destinations. Use {@link LoggingManagerInternal#captureSystemSources()} to enable this.</li>
     * <li>Configure java util logging. Use {@link LoggingManagerInternal#captureSystemSources()} to enable this.</li>
     * <li>Route logging output to the original System.out and System.err. Use {@link LoggingManagerInternal#attachSystemOutAndErr()} to enable this.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     */
    public static LoggingServiceRegistry newEmbeddableLogging() {
        return new CommandLineLogging();
    }

    /**
     * Creates a set of logging services to set up a new logging scope that does nothing by default. The methods on {@link LoggingManagerInternal} can be used to configure the
     * logging services do useful things.
     *
     * <p>Sets log level to {@link LogLevel#LIFECYCLE}.</p>
     */
    public static LoggingServiceRegistry newNestedLogging() {
        return new NestedLogging();
    }

    protected Clock createTimeProvider() {
        return Time.clock();
    }

    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(getStdoutListener(), get(Clock.class));
    }

    protected TextStreamOutputEventListener getStdoutListener() {
        if (stdoutListener == null) {
            stdoutListener = new TextStreamOutputEventListener(get(OutputEventListenerManager.class).getBroadcaster());
        }
        return stdoutListener;
    }

    protected DefaultLoggingManagerFactory createLoggingManagerFactory() {
        OutputEventListener outputEventBroadcaster = outputEventListenerManager.getBroadcaster();

        LoggingSourceSystem stdout = new DefaultStdOutLoggingSystem(getStdoutListener(), get(Clock.class));
        stdout.setLevel(LogLevel.QUIET);
        LoggingSourceSystem stderr = new DefaultStdErrLoggingSystem(new TextStreamOutputEventListener(outputEventBroadcaster), get(Clock.class));
        stderr.setLevel(LogLevel.ERROR);
        return new DefaultLoggingManagerFactory(
                renderer,
                new LoggingSystemAdapter(new Slf4jLoggingConfigurer(outputEventBroadcaster)),
                new JavaUtilLoggingSystem(),
                stdout,
                stderr);
    }

    protected OutputEventListener createOutputEventListener(OutputEventListenerManager manager) {
        return manager.getBroadcaster();
    }

    protected OutputEventListenerManager createOutputEventListenerManager() {
        return outputEventListenerManager;
    }

    // Intentionally not a “create” method as this should not be exposed as a service
    protected OutputEventRenderer makeOutputEventRenderer() {
        return new OutputEventRenderer(Time.clock());
    }

    private static class CommandLineLogging extends LoggingServiceRegistry {
    }

    private static class NestedLogging extends LoggingServiceRegistry {

        @Override
        protected DefaultLoggingManagerFactory createLoggingManagerFactory() {
            // Don't configure anything
            return new DefaultLoggingManagerFactory(
                    renderer,
                    new NoOpLoggingSystem(),
                    new NoOpLoggingSystem(),
                    new NoOpLoggingSystem(),
                    new NoOpLoggingSystem()
            );
        }
    }
}
