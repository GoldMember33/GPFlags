package me.ryanhamshire.GPFlags.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GPFlags.*;
import me.ryanhamshire.GPFlags.flags.FlagDefinition;
import me.ryanhamshire.GPFlags.util.MessagingUtil;
import me.ryanhamshire.GPFlags.util.Util;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlaceholderApiHook extends PlaceholderExpansion {

    private final GPFlags plugin;

    public PlaceholderApiHook(GPFlags plugin) {
        this.plugin = plugin;
    }

    /**
     * Used to add oter plugin's placeholders to GPFlags messages
     * @param player Player context for placeholders that use it
     * @param message String before placeholders are added
     * @return String with placeholders added
     */
    public static String addPlaceholders(OfflinePlayer player, String message) {
        return PlaceholderAPI.setPlaceholders(player, message);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gpflags";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * This method is used for listing all GPFlags placeholders
     * on performing "/papi info gpflags" command and some
     * tab completions.
     *
     * @return: A {@link List} of placeholders of this plugin.
     */
    @Override
    public @NotNull List<String> getPlaceholders() {
        List<String> placeholders = new ArrayList<>();

        placeholders.add("%gpflags_cansetclaimflag_<flag-name>%");
        placeholders.add("%gpflags_isflagactive_<flag-name>%");
        placeholders.add("%gpflags_flagparam_<flag-name>_<index>%");
        placeholders.add("%gpflags_flagparams_<flag-name>%");
        placeholders.add("%gpflags_claimactiveflags%");

        return placeholders;

    }

    @Override
    public boolean persist() {
        return true; // Keep registered even if PlaceholderAPI reloads
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        if (!(offlinePlayer instanceof Player)) return null;
        Player player = (Player) offlinePlayer;

        // Splitting the placeholder parts into an array (not includes the 'gpflags' identifier)
        String[] placeholderParametersArray = identifier.split("_");

        // Get the placeholder main part / parameter.
        String mainPlaceholderParameter = placeholderParametersArray[0];

        //String flagName = identifier.substring(identifier.indexOf('_') + 1);

        // %gpflags_cansetclaimflag_<flag-name>%
        //
        // This placeholder checks whether a certain player will be able to
        // set a flag inside a claim protection (So, if he has the permission
        // to do / perform this operation).
        if (mainPlaceholderParameter.equalsIgnoreCase("cansetclaimflag") && placeholderParametersArray.length == 2) {

            // Get the claim flag name to parse the placeholder.
            String flagName = placeholderParametersArray[1];

            // Check perms for that specific flag
            if (!player.hasPermission("gpflags.flag." + flagName)) {
                MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoFlagPermission, flagName);
                return "No";
            }

            // Check that the flag can be used in claims
            FlagDefinition def = plugin.getFlagManager().getFlagDefinitionByName(flagName);
            if (!def.getFlagType().contains(FlagDefinition.FlagType.CLAIM)) {
                MessagingUtil.sendMessage(player, TextMode.Err, Messages.NoFlagInClaim);
                return "No";
            }

            // Check that they are standing in a claim
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            if (claim == null) {
                return "No";
            }

            // Check that they can set flags in the area
            if (!Util.canEdit(player, claim)) {
                MessagingUtil.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
                return "No";
            }

            return "Yes";
        }

        // %gpflags_isflagactive_<flag-name>%
        //
        // This placeholder retrieves an indication whether the flag inside a claim
        // protection is active or not (return 'Yes' for enabled or 'No' for disabled).
        if (mainPlaceholderParameter.equalsIgnoreCase("isflagactive") && placeholderParametersArray.length == 2) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);

            // Get the claim flag name to parse the placeholder.
            String flagName = placeholderParametersArray[1];
            Flag flag = plugin.getFlagManager().getEffectiveFlag(player.getLocation(), flagName, claim);
            if (flag == null) return "No";
            return "Yes";
        }

        // %gpflags_flagparam_<flag-name>_<index>%
        //
        // Retrieve a claim flag parameter depending on name and the index of it,
        // since there is a possibility to have more than one for that flag.
        // Return "None" if there are no suitable claim, invalid specified index,
        // no array mapping value for this index or an error occurs in these steps.
        //
        // For example, like the NoMobSpawnsType claim flag.
        if (mainPlaceholderParameter.equalsIgnoreCase("flagparam") && placeholderParametersArray.length == 3) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false,
                    playerData.lastClaim);
            if (claim == null) return "None";

            // Get the claim flag name to parse the placeholder.
            String flagName = placeholderParametersArray[1];

            Flag flag = plugin.getFlagManager().getEffectiveFlag(
                    claim.getGreaterBoundaryCorner(),
                    flagName,
                    claim
            );

            if (flag == null) return "None";

            // Calculating and checking the parsed placeholder index value
            // to get the specified claim flag parameter by this array position.
            int index;

            try {

                String[] flagParameters = flag.getParameters().split(",");

                index = Integer.parseInt(placeholderParametersArray[2]);
                if (index < 0 || index > flagParameters.length - 1) {
                    return "None";
                }

                String flagParameter = flagParameters[index];
                if (flagParameter == null || flagParameter.isEmpty()) return "None";

                return flagParameter;

            } catch (Exception exception) {
                exception.printStackTrace();
                return "None";
            }
        }

        // %gpflags_flagparams_<flag-name>%
        //
        // This placeholder retrieves all the active claim flag parameters as a list (separated by ', ').
        if (mainPlaceholderParameter.equalsIgnoreCase("flagparams") && placeholderParametersArray.length == 2) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false,
                    playerData.lastClaim);

            if (claim == null) return "None";

            // Get the claim flag name to parse the placeholder.
            String flagName = placeholderParametersArray[1];

            // Get an array of String with claim flag parameters.
            String[] parametersArray = this.getFlagParametersArray(claim, flagName);

            if (!Arrays.asList(parametersArray).isEmpty()) {
                return "None";
            }

            return String.join(", ", parametersArray);
        }

        // %gpflags_claimactiveflags%
        //
        // This placeholder will be used to retrieve a list (separated by ', ')
        // of active or used flags in the claim which player is in.
        if (mainPlaceholderParameter.equalsIgnoreCase("claimactiveflags")) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId());

            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), false, playerData.lastClaim);
            if (claim == null) return "None";

            // Retrieve a list of flags which are used in the claim
            List<Flag> claimUsedFlags = new ArrayList<>(plugin.getFlagManager().getFlags(claim));
            if (claimUsedFlags.isEmpty()) {
                return "None";
            }

            return String.join(", ", claimUsedFlags.stream().map(flag ->
                    flag.getFlagDefinition().getName()).toArray(String[]::new));
        }

        return null;
    }

    /**
     * Retrieve an array with type of {@link String} containing
     * the saved parameters depending on a certain claim flag.
     *
     * @param claim: The {@link Claim} protection to retrieve parameters.
     * @param flagName: The {@link String} name of the claim flag.
     *
     * @return: An array with claim flag parameters.
     */
    private String[] getFlagParametersArray(Claim claim, String flagName) {
        Flag flag = plugin.getFlagManager().getEffectiveFlag(
                claim.getGreaterBoundaryCorner(),
                flagName,
                claim
        );

        if (flag == null) return new String[]{};

        return flag.getParametersArray();
    }
}
