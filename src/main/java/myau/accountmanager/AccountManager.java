package myau.accountmanager;

import com.google.gson.*;
import myau.accountmanager.auth.Account;
import myau.accountmanager.utils.Nan0EventRegister;
import myau.accountmanager.utils.SSLUtils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Optional;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class AccountManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final File file = resolveAccountFile();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static final ArrayList<Account> accounts = new ArrayList<>();

    private static File resolveAccountFile() {
        File accountFile = new File(new File(Minecraft.getMinecraft().mcDataDir, "CrewX"), "accounts.json");
        if (accountFile.exists()) {
            return accountFile;
        }

        String[] legacyNames = {"ruuks.accounts.json", "keystrokes.accounts.json"};
        for (String legacyName : legacyNames) {
            File legacyFile = new File(mc.mcDataDir, legacyName);
            if (!legacyFile.isFile()) {
                continue;
            }

            try {
                Files.move(legacyFile.toPath(), accountFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (IOException moveError) {
                try {
                    Files.copy(legacyFile.toPath(), accountFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    break;
                } catch (IOException copyError) {
                    System.err.println("Couldn't migrate legacy account data: " + copyError.getMessage());
                }
            }
        }
        return accountFile;
    }

    public static void init() {
        SSLContext ignored = SSLUtils.getSSLContext();
        Nan0EventRegister.register(MinecraftForge.EVENT_BUS,new Events());

        if (!file.exists()) {
            try {
                if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
                    if (file.createNewFile()) {
                        System.out.print("Successfully created accounts file!");
                    }
                }
            } catch (IOException e) {
                System.err.print("Couldn't create accounts file!");
            }
        }
    }

    public static void load() {
        accounts.clear();
        try {
            JsonElement json = new JsonParser().parse(
                    new BufferedReader(new FileReader(file))
            );
            if (json instanceof JsonArray) {
                JsonArray jsonArray = json.getAsJsonArray();
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    accounts.add(new Account(
                            Optional.ofNullable(jsonObject.get("refreshToken")).map(JsonElement::getAsString).orElse(""),
                            Optional.ofNullable(jsonObject.get("accessToken")).map(JsonElement::getAsString).orElse(""),
                            Optional.ofNullable(jsonObject.get("username")).map(JsonElement::getAsString).orElse(""),
                            Optional.ofNullable(jsonObject.get("unban")).map(JsonElement::getAsLong).orElse(0L),
                            Optional.ofNullable(jsonObject.get("clientId")).map(JsonElement::getAsString).orElse(""),
                            Optional.ofNullable(jsonObject.get("scope")).map(JsonElement::getAsString).orElse(""),
                            Optional.ofNullable(jsonObject.get("uuid")).map(JsonElement::getAsString).orElse(""),
                            Account.Type.fromStorage(
                                    Optional.ofNullable(jsonObject.get("type")).map(JsonElement::getAsString).orElse(null),
                                    Optional.ofNullable(jsonObject.get("refreshToken")).map(JsonElement::getAsString).orElse("")
                            ),
                            Optional.ofNullable(jsonObject.get("skinHash")).map(JsonElement::getAsString).orElse(""),
                            Optional.ofNullable(jsonObject.get("skinSlim")).map(JsonElement::getAsBoolean).orElse(false)
                    ));
                }
            }
        } catch (FileNotFoundException e) {
            System.err.print("Couldn't find accounts file!");
        }
    }

    public static void save() {
        try {
            JsonArray jsonArray = new JsonArray();
            for (Account account : accounts) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("refreshToken", account.getRefreshToken());
                jsonObject.addProperty("accessToken", account.getAccessToken());
                jsonObject.addProperty("username", account.getUsername());
                jsonObject.addProperty("unban", account.getUnban());
                jsonObject.addProperty("clientId", account.getClientId());
                jsonObject.addProperty("scope", account.getScope());
                jsonObject.addProperty("offline", "offline".equals(account.getRefreshToken()));
                jsonObject.addProperty("uuid", account.getUuid());
                jsonObject.addProperty("type", account.getType().name());
                jsonObject.addProperty("skinHash", account.getSkinHash());
                jsonObject.addProperty("skinSlim", account.isSkinSlim());
                jsonArray.add(jsonObject);
            }
            PrintWriter printWriter = new PrintWriter(new FileWriter(file));
            printWriter.println(gson.toJson(jsonArray));
            printWriter.close();
        } catch (IOException e) {
            System.err.print("Couldn't save accounts file!");
        }
    }

}
