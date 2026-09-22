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
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                setBottomNavVisible(isTopLevel(destination)));

        applyWindowInsets();
    }

    private static boolean isTopLevel(NavDestination destination) {
        return TOP_LEVEL.contains(destination.getId());
    }

    private void setBottomNavVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (binding.bottomNav.getVisibility() != visibility) {
            binding.bottomNav.setVisibility(visibility);
            ViewCompat.requestApplyInsets(binding.getRoot());
        }
    }

    /**
     * Status bar → app bar top padding. Keyboard / navigation bar → content bottom padding.
     * The bottom navigation view pads itself for the navigation bar (Material default), so the
     * content only needs the keyboard part that overlaps it.
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            binding.appBar.setPadding(bars.left, bars.top, bars.right, 0);
            boolean bottomNavVisible = binding.bottomNav.getVisibility() == View.VISIBLE;
            int bottom = bottomNavVisible
                    ? Math.max(0, ime.bottom - binding.bottomNav.getHeight())
                    : Math.max(bars.bottom, ime.bottom);
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
