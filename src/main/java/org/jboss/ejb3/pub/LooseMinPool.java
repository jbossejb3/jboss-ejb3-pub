/*
 * JBoss, Home of Professional Open Source
 * Copyright (c) 2011, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.ejb3.pub;

import org.jboss.ejb3.BeanContext;
import org.jboss.ejb3.Container;
import org.jboss.ejb3.pool.AbstractPool;
import org.jboss.ejb3.pool.Pool;
import org.jboss.injection.Injector;
import org.jboss.logging.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
 */
public class LooseMinPool extends AbstractPool implements Pool {
    /* --- constants -------------------------------------------------- */

    public final static int DEFAULT_INITSIZE = 100; // initial: array.length == 100
    public final static int DEFAULT_MAXSIZE = Integer.MAX_VALUE; // there's no maximum!
    public final static int DEFAULT_TIMEOUT = 30 * 60 * 1000; // timeout: 30 minutes

    public final static DateFormat DATEFORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    /* --- members -------------------------------------------------- */

    /**
     * array thats stores free, pooled instances
     */
    private BeanContextWrapper[] pool = new BeanContextWrapper[DEFAULT_INITSIZE];
    /**
     * amount of instances in the array
     */
    private int poolSize = 0;

    /**
     * lock to synchronize pool access
     */
    private Lock lock = new ReentrantLock(false);

    /**
     * amount of instances that are currently in use
     */
    private int inUse = 0;

    /**
     * maximum pool size (not used!!)
     */
    private int maxSize = DEFAULT_MAXSIZE;

    /**
     * duration until next pool cleaning
     */
    private long timeout = DEFAULT_TIMEOUT;
    /**
     * date when to clear pool
     */
    private long nextTimeout = System.currentTimeMillis() + timeout;

    /**
     * statistics
     */
    private int statMaxInUse = 0;
    private int statMaxSize = 0;
    private int statMaxTimedout = 0;

    /**
     * logger
     */
    private Logger logger = Logger.getLogger(getClass());

    /* --- constructors -------------------------------------------------- */

    /**
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    public LooseMinPool() {
        super();
    }

    /* --- configuration/ initialization -------------------------------------------------- */

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @SuppressWarnings("unchecked")
    @Override
    public void initialize(Container container, int maxSize, long timeout) {
        // maxSize and timeout not used by AbstractPool!
        super.initialize(container, -1, -1);

        // maximum pool size (not used!)
        this.maxSize = maxSize;
        // duration until next pool cleaning
        this.timeout = timeout;
        // date when to clear pool: now!
        nextTimeout = System.currentTimeMillis();

        if (logger.isTraceEnabled()) {
            logger.trace("initialized pool " + toString());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public void setInjectors(Injector[] injectors) {
        super.setInjectors(injectors);
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public void setMaxSize(int maxSize) {
        // maximum pool size (not used!)
        this.maxSize = maxSize;
    }

    /* --- pool implementation -------------------------------------------------- */

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public BeanContext get() {
        return get(null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @SuppressWarnings("unchecked")
    @Override
    public BeanContext get(Class[] types, Object[] values) {
        if (logger.isTraceEnabled()) {
            logger.trace("get instance " + toString());
        }

        BeanContext context = null;

        lock.lock();
        try {
            // get last array element
            BeanContextWrapper wrapper = pool[poolSize - 1];
            context = wrapper.context;
            // clear last array element
            pool[poolSize - 1] = null;
            poolSize--;
        } catch (ArrayIndexOutOfBoundsException e) {
            // there is not element in the array (arraySize == 0)
        } finally {
            inUse++;
            // statistics
            statMaxInUse = ((inUse > statMaxInUse) ? inUse : statMaxInUse);

            lock.unlock();
        }

        if (context == null) {
            // there was no instance in pool, create new one
            if (types == null && values == null) {
                context = create();
            } else {
                context = create(types, values);
            }
        }

        return context;
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    protected BeanContext create() {
        if (logger.isDebugEnabled()) {
            logger.debug("create instance " + toString());
        }

        return super.create();
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @SuppressWarnings("unchecked")
    @Override
    protected BeanContext create(Class[] types, Object[] values) {
        if (logger.isDebugEnabled()) {
            logger.debug("create instance " + toString());
        }

        return super.create(types, values);
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public void release(BeanContext context) {
        if (logger.isTraceEnabled()) {
            logger.trace("release instance " + toString());
        }

        // time to clear pool?
        timeout();

        try {
            lock.lock();

            // put instance back into pool
            BeanContextWrapper wrapper = new BeanContextWrapper(context);
            wrapper.released = System.currentTimeMillis(); // remember last access
            try {
                pool[poolSize] = wrapper;
                poolSize++;
            } catch (ArrayIndexOutOfBoundsException e) {
                // does not fit in array, enlarge it
                int length = (pool.length + DEFAULT_INITSIZE);
                pool = Arrays.copyOf(pool, length);
                pool[poolSize] = wrapper;
                poolSize++;
            } finally {
                inUse--;
                // statistics
                statMaxSize = ((poolSize > statMaxSize) ? poolSize : statMaxSize);

                lock.unlock();
            }
        } catch (Exception e) {
            logger.warn(null, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public void discard(BeanContext context) {
        if (logger.isDebugEnabled()) {
            logger.debug("discard instance " + toString());
        }

        lock.lock();
        try {
            inUse--;
        } finally {
            lock.unlock();
        }

        super.discard(context);
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public void remove(BeanContext context) {
        if (logger.isDebugEnabled()) {
            logger.debug("remove instance " + toString());
        }

        super.remove(context);
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public void destroy() {
        // destroy all pooled instances
        BeanContextWrapper[] destroy = null;
        int destroySize = 0;

        lock.lock();
        try {
            destroy = pool;
            destroySize = poolSize;
            // create new array
            pool = new BeanContextWrapper[pool.length];
            poolSize = 0;
            inUse = 0;
        } finally {
            lock.unlock();
        }

        if (destroySize > 0 && logger.isDebugEnabled()) {
            logger.debug("destroy " + destroySize + " instance(s) " + toString());
        } else if (logger.isTraceEnabled()) {
            logger.trace("destroy " + destroySize + " instance(s) " + toString());
        }

        // remove instances
        for (int i = 0; i < destroySize; i++) {
            BeanContextWrapper wrapper = destroy[i];
            remove(wrapper.context);
        }
    }

    /**
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    private void timeout() {
        long now = System.currentTimeMillis();
        if (now >= nextTimeout) {
            long limit = (now - timeout);

            BeanContextWrapper[] timedout = null;
            int timedoutSize = 0;

            lock.lock();
            try {
                if (poolSize <= 0) {
                    // pool is empty, so not timedout instances
                    timedout = new BeanContextWrapper[0];
                    timedoutSize = 0;
                } else {
                    timedout = new BeanContextWrapper[poolSize]; // clone!
                    BeanContextWrapper[] resume = new BeanContextWrapper[pool.length]; // clear!
                    int resumeSize = 0;

                    for (int i = 0; i < poolSize; i++) {
                        BeanContextWrapper wrapper = pool[i];
                        if (wrapper.released < limit) {
                            // instance was not accessed since last timeout
                            timedout[timedoutSize++] = wrapper;
                        } else {
                            // instance was accessed since last timeout
                            resume[resumeSize++] = wrapper;
                        }
                    }

                    // update pool
                    pool = resume;
                    poolSize = resumeSize;
                }
            } finally {
                lock.unlock();
            }

            // update timeout
            nextTimeout = (nextTimeout + timeout);
            // statistics
            statMaxTimedout = ((timedoutSize > statMaxTimedout) ? timedoutSize : statMaxTimedout);

            if (timedoutSize > 0 && logger.isDebugEnabled()) {
                logger.debug("timedout " + timedoutSize + " instance(s) " + toString());
            } else if (logger.isTraceEnabled()) {
                logger.trace("timedout " + timedoutSize + " instance(s) " + toString());
            }

            // remove timedout intances
            for (int i = 0; i < timedoutSize; i++) {
                BeanContextWrapper wrapper = timedout[i];
                remove(wrapper.context);
            }
        }
    }

    /* --- statistiks -------------------------------------------------- */

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public int getAvailableCount() {
        // amount of instances in the array
        return poolSize;
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public int getCreateCount() {
        return super.getCreateCount();
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public int getCurrentSize() {
        // amount of instances in the array and instances currently in use
        return poolSize + inUse;
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public int getMaxSize() {
        // not used!!
        return maxSize;
    }

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public int getRemoveCount() {
        return super.getRemoveCount();
    }

    /* --- miscellaneous -------------------------------------------------- */

    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append(getClass().getSimpleName());
        string.append("[");
        string.append("beanName=").append(((container == null) ? "<NULL>" : container.getEjbName()));
        string.append(" availableCount=" + getAvailableCount());
        string.append(" currentSize=" + getCurrentSize());
        string.append(" inUse=").append(inUse);
        string.append(" maxSize=").append(statMaxSize);
        string.append(" maxInUse=").append(statMaxInUse);
        string.append(" maxTimedout=").append(statMaxTimedout);
        string.append(" createCount=" + getCreateCount());
        string.append(" removeCount=" + getRemoveCount());
        string.append(" maxSize=" + maxSize);
        string.append(" timeout=" + timeout);
        string.append(" nextTimeout=" + DATEFORMAT.format(new Date(nextTimeout)));
        string.append(")");
        string.append("]");
        return string.toString();
    }

    /* --- inner classes -------------------------------------------------- */

    /**
     * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
     */
    class BeanContextWrapper {
        BeanContext context;
        long released = 0;

        /**
         * @param context
         * @author <a href="mailto:robert.geisler@gmx.de">robert geisler</a>
         */
        BeanContextWrapper(BeanContext context) {
            super();
            this.context = context;
        }
    }
}