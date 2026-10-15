package crewx.event;

import crewx.event.events.Event;
import crewx.event.events.EventStoppable;
import crewx.event.types.Priority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EventManager {

    private static final HashMap<Class<? extends Event>, List<MethodData>> REGISTRY_MAP = new HashMap<>();
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

    private EventManager() {
    }

    public static void register(Object object) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method)) {
                register(method, object);
            }
        }
    }

    public static void register(Object object, Class<? extends Event> eventClass) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method, eventClass)) {
                register(method, object);
            }
        }
    }

    public static void unregister(Object object) {
        for (final List<MethodData> dataList : REGISTRY_MAP.values()) {
            for (final MethodData data : dataList) {
                if (data.getSource().equals(object)) {
                    dataList.remove(data);
                }
            }
        }
        cleanMap(true);
    }

    public static void unregister(Object object, Class<? extends Event> eventClass) {
        if (REGISTRY_MAP.containsKey(eventClass)) {
            for (final MethodData data : REGISTRY_MAP.get(eventClass)) {
                if (data.getSource().equals(object)) {
                    REGISTRY_MAP.get(eventClass).remove(data);
                }
            }
            cleanMap(true);
        }
    }

    private static void register(Method method, Object object) {
        Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0];

        final MethodData data = new MethodData(object, method, method.getAnnotation(EventTarget.class).value());

        if (!data.getTarget().isAccessible()) {
            data.getTarget().setAccessible(true);
        }
        if (REGISTRY_MAP.containsKey(indexClass)) {
            if (!REGISTRY_MAP.get(indexClass).contains(data)) {
                REGISTRY_MAP.get(indexClass).add(data);
                sortListValue(indexClass);
            }
        } else {
            REGISTRY_MAP.put(indexClass, new CopyOnWriteArrayList<MethodData>() {

                private static final long serialVersionUID = 666L;

                {
                    add(data);
                }
            });
        }
    }

    public static void removeEntry(Class<? extends Event> indexClass) {
        Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = REGISTRY_MAP.entrySet().iterator();
        while (mapIterator.hasNext()) {
            if (mapIterator.next().getKey().equals(indexClass)) {
                mapIterator.remove();
                break;
            }
        }
    }

    public static void cleanMap(boolean onlyEmptyEntries) {
        if (!onlyEmptyEntries) {
            REGISTRY_MAP.clear();
            return;
        }
        Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = REGISTRY_MAP.entrySet().iterator();
        while (mapIterator.hasNext()) {
            if (!onlyEmptyEntries || mapIterator.next().getValue().isEmpty()) {
                mapIterator.remove();
            }
        }
    }

    private static void sortListValue(Class<? extends Event> indexClass) {
        List<MethodData> sortedList = new CopyOnWriteArrayList<>();
        for (final byte priority : Priority.VALUE_ARRAY) {
            for (final MethodData data : REGISTRY_MAP.get(indexClass)) {
                if (data.getPriority() == priority) {
                    sortedList.add(data);
                }
            }
        }

        REGISTRY_MAP.put(indexClass, sortedList);
    }

    private static boolean isMethodBad(Method method) {
        return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventTarget.class);
    }

    private static boolean isMethodBad(Method method, Class<? extends Event> eventClass) {
        return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
    }

    public static Event call(final Event event) {
        List<MethodData> dataList = REGISTRY_MAP.get(event.getClass());
        if (dataList != null) {
            if (event instanceof EventStoppable) {
                EventStoppable stoppable = (EventStoppable) event;
                for (final MethodData data : dataList) {
                    invoke(data, event);
                    if (stoppable.isStopped()) {
                        break;
                    }
                }
            } else {
                for (final MethodData data : dataList) {
                    invoke(data, event);
                }
            }
        }
        return event;
    }

    private static void invoke(MethodData data, Event argument) {
        try {
            if (crewx.util.HitchMonitor.isEnabled()) {
                long start = System.nanoTime();
                data.call(argument);
                crewx.util.HitchMonitor.handler(data, System.nanoTime() - start);
            } else {
                data.call(argument);
            }
        } catch (Exception e) {
            // as before: whatever a handler throws arrives wrapped by Method.invoke and is logged once
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null
                    ? e.getCause() : e;
            String key = data.getSource().getClass().getName() + '#'
                    + data.getTarget().getName() + ':' + cause.getClass().getName()
                    + ':' + cause.getMessage();
            if (REPORTED_FAILURES.add(key)) {
                crewx.util.ClientLog.log("event handler FAILED " + key + " "
                        + (cause.getStackTrace().length == 0 ? "" : cause.getStackTrace()[0]));
            }
        }
    }

    private static final ThreadLocal<Object[]> ARGS = new ThreadLocal<Object[]>() {
        @Override
        protected Object[] initialValue() {
            return new Object[1];
        }
    };

    private static final class MethodData {
        private final Object source;
        private final Method target;
        private final byte priority;

        private String label;

        public MethodData(Object source, Method target, byte priority) {
            this.source = source;
            this.target = target;
            this.priority = priority;
        }

        /**
         * Method.invoke(source, event) builds a new argument array for every call of every handler, every frame;
         * passing a reused one-element array allocates nothing. Reflection unpacks the array before the handler
         * runs, so a nested event dispatched from inside a handler may safely reuse it; one array per thread
         * because packet events arrive on the network thread.
         */
        void call(Event argument) throws Exception {
            Object[] args = ARGS.get();
            args[0] = argument;
            try {
                this.target.invoke(this.source, args);
            } finally {
                args[0] = null;
            }
        }

        /** "Module.method", for the hitch report. */
        @Override
        public String toString() {
            String text = this.label;
            if (text == null) this.label = text = this.source.getClass().getSimpleName() + "." + this.target.getName();
            return text;
        }

        public Object getSource() {
            return source;
        }

        public Method getTarget() {
            return target;
        }

        public byte getPriority() {
            return priority;
        }
    }
}
