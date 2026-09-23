package io.github.thiagojosetj.gym.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ActivityMainBinding;

/**
 * The only activity. It owns the app bar and the bottom navigation; every screen is a fragment
 * swapped by the Navigation Component (ARCHITECTURE §5.4).
 */
public class MainActivity extends AppCompatActivity implements TabNavigator {

    /** Destinations reachable from the bottom navigation (no "up" arrow, bottom bar visible). */
    private static final Set<Integer> TOP_LEVEL = new HashSet<>(Arrays.asList(
            R.id.homeFragment, R.id.templateListFragment, R.id.exerciseLibraryFragment));

    private ActivityMainBinding binding;
    private NavController navController;
    /** Whether the current screen is a bottom-navigation tab (read by the insets listener). */
    private boolean topLevelDestination = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Required behaviour for targetSdk 35+: draw behind the system bars, then pad with insets.
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        navController = host.getNavController();
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(TOP_LEVEL).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            topLevelDestination = isTopLevel(destination);
            ViewCompat.requestApplyInsets(binding.getRoot());
        });

        applyWindowInsets();
    }

    private static boolean isTopLevel(NavDestination destination) {
        return TOP_LEVEL.contains(destination.getId());
    }

    /**
     * Status bar → app bar top padding; navigation bar / keyboard → content bottom padding.
     *
     * <p>The bottom navigation is hidden while the keyboard is open: it would only steal space from
     * the form, and hiding it keeps the padding math independent of how the Material component
     * handles IME insets itself. Everything is decided in this single pass over the same insets.
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            binding.appBar.setPadding(bars.left, bars.top, bars.right, 0);
            boolean showBottomNav = topLevelDestination && !imeVisible;
            binding.bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
            // With the bar visible it sits below the content and pads itself for the navigation bar.
            int bottom = showBottomNav ? 0 : Math.max(bars.bottom, ime.bottom);
            binding.navHost.setPadding(bars.left, 0, bars.right, bottom);
            return insets;
        });
    }

    /**
     * Routes "up" through the back dispatcher so screens with unsaved changes (template editor)
     * can intercept it exactly like the system back gesture.
     */
    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public void selectTab(@IdRes int destinationId) {
        binding.bottomNav.setSelectedItemId(destinationId);
    }
}
