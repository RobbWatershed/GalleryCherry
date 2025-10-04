package me.devsaki.hentoid.mocks;

import static java.sql.DriverManager.println;

import androidx.test.core.app.ApplicationProvider;

import com.google.firebase.FirebaseApp;

import net.lachlanmckee.timberjunit.TimberTestRule;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

import java.io.File;

import io.objectbox.BoxStore;
import me.devsaki.hentoid.database.domains.MyObjectBox;
import timber.log.Timber;

public abstract class AbstractObjectBoxTest {
    private static final File TEST_DIRECTORY = new File("objectbox-example/test-db");
    protected static BoxStore store;

    @Rule
    public TimberTestRule logAllAlwaysRule = TimberTestRule.logAllAlways();

    @BeforeClass
    public static void setUp() throws Exception {
        println(">> Setting up DB...");
        // delete database files before each test to start with a clean database
        BoxStore.deleteAllFiles(TEST_DIRECTORY);
        store = MyObjectBox.builder()
                // add directory flag to change where ObjectBox puts its database files
                .directory(TEST_DIRECTORY)
                .build();
        println(">> DB set up");
    }

    @BeforeClass
    public static void prepareTimber() {
        Timber.plant(new Timber.DebugTree());
    }

    @Before // Crashes when used inside @BeforeClass. Only valid way to use that is inside @Before
    public void prepareSupportTools() {
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
    }


    @AfterClass
    public static void tearDown() throws Exception {
        if (store != null) {
            store.closeThreadResources();
            store.close();
            store = null;
        }
        BoxStore.deleteAllFiles(TEST_DIRECTORY);
    }
}
