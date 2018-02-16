package com.linbit.linstor.security;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.linbit.GuiceConfigModule;
import com.linbit.ImplementationError;
import com.linbit.InvalidNameException;
import com.linbit.ControllerLinstorModule;
import com.linbit.SatelliteLinstorModule;
import com.linbit.drbd.md.MetaDataModule;
import com.linbit.linstor.ControllerDatabase;
import com.linbit.linstor.LinStorModule;
import com.linbit.linstor.core.ApiCallHandlerModule;
import com.linbit.linstor.core.ConfigModule;
import com.linbit.linstor.core.Controller;
import com.linbit.linstor.core.ControllerCoreModule;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.CtrlApiCallHandlerModule;
import com.linbit.linstor.core.LinStorArguments;
import com.linbit.linstor.core.LinStorArgumentsModule;
import com.linbit.linstor.core.Satellite;
import com.linbit.linstor.core.SatelliteCoreModule;
import com.linbit.linstor.dbcp.DbConnectionPoolModule;
import com.linbit.linstor.dbdrivers.DbDriversModule;
import com.linbit.linstor.debug.ControllerDebugModule;
import com.linbit.linstor.debug.DebugModule;
import com.linbit.linstor.debug.SatelliteDebugModule;
import com.linbit.linstor.drbdstate.DrbdStateModule;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.logging.LoggingModule;
import com.linbit.linstor.netcom.NetComModule;
import com.linbit.linstor.numberpool.NumberPoolModule;
import com.linbit.linstor.timer.CoreTimerModule;

import java.sql.SQLException;

/**
 * Initializes Controller and Satellite instances with the system's security context
 *
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
public final class Initializer
{
    private static final AccessContext SYSTEM_CTX;
    private static final AccessContext PUBLIC_CTX;

    static
    {
        PrivilegeSet sysPrivs = new PrivilegeSet(Privilege.PRIV_SYS_ALL);

        // Create the system's security context
        SYSTEM_CTX = new AccessContext(
            Identity.SYSTEM_ID,
            Role.SYSTEM_ROLE,
            SecurityType.SYSTEM_TYPE,
            sysPrivs
        );

        PrivilegeSet publicPrivs = new PrivilegeSet();

        PUBLIC_CTX = new AccessContext(
            Identity.PUBLIC_ID,
            Role.PUBLIC_ROLE,
            SecurityType.PUBLIC_TYPE,
            publicPrivs
        );
    }

    public Initializer()
    {
        try
        {
            AccessContext initCtx = SYSTEM_CTX.clone();
            initCtx.getEffectivePrivs().enablePrivileges(Privilege.PRIV_SYS_ALL);

            // Adjust the type enforcement rules for the SYSTEM domain/type
            SecurityType.SYSTEM_TYPE.addRule(
                initCtx,
                SecurityType.SYSTEM_TYPE, AccessType.CONTROL
            );
        }
        catch (AccessDeniedException accessExc)
        {
            throw new ImplementationError(
                "The built-in SYSTEM security context has insufficient privileges " +
                "to initialize the security subsystem.",
                accessExc
            );
        }
    }

    public Controller initController(LinStorArguments cArgs, ErrorReporter errorLog)
        throws AccessDeniedException
    {
        AccessContext initCtx = SYSTEM_CTX.clone();
        initCtx.getEffectivePrivs().enablePrivileges(Privilege.PRIV_SYS_ALL);

        Injector injector = Guice.createInjector(new GuiceConfigModule(),
            new LoggingModule(errorLog),
            new SecurityModule(initCtx),
            new ControllerSecurityModule(),
            new LinStorArgumentsModule(cArgs),
            new ConfigModule(),
            new CoreTimerModule(),
            new MetaDataModule(),
            new ControllerLinstorModule(),
            new LinStorModule(),
            new CoreModule(),
            new ControllerCoreModule(),
            new DbDriversModule(),
            new DbConnectionPoolModule(),
            new NetComModule(),
            new NumberPoolModule(),
            new ApiCallHandlerModule(),
            new CtrlApiCallHandlerModule(),
            new DebugModule(),
            new ControllerDebugModule()
        );

        return new Controller(injector, SYSTEM_CTX, PUBLIC_CTX);
    }

    public Satellite initSatellite(LinStorArguments cArgs, ErrorReporter errorLog)
        throws AccessDeniedException
    {
        AccessContext initCtx = SYSTEM_CTX.clone();
        initCtx.getEffectivePrivs().enablePrivileges(Privilege.PRIV_SYS_ALL);

        Injector injector = Guice.createInjector(new GuiceConfigModule(),
            new LoggingModule(errorLog),
            new SecurityModule(initCtx),
            new SatelliteSecurityModule(),
            new LinStorArgumentsModule(cArgs),
            new CoreTimerModule(),
            new SatelliteLinstorModule(),
            new CoreModule(),
            new SatelliteCoreModule(),
            new DrbdStateModule(),
            new ApiCallHandlerModule(),
            new DebugModule(),
            new SatelliteDebugModule()
        );

        return new Satellite(injector, SYSTEM_CTX, PUBLIC_CTX);
    }

    public static void load(AccessContext accCtx, ControllerDatabase ctrlDb, DbAccessor driver)
        throws SQLException, AccessDeniedException, InvalidNameException
    {
        accCtx.getEffectivePrivs().requirePrivileges(Privilege.PRIV_SYS_ALL);

        SecurityLevel.load(ctrlDb, driver);
        Identity.load(ctrlDb, driver);
        SecurityType.load(ctrlDb, driver);
        Role.load(ctrlDb, driver);
    }
}
