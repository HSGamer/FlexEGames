package me.hsgamer.flexegames.util;

import com.google.gson.Gson;
import lombok.experimental.UtilityClass;
import net.minestom.server.permission.Permission;
import org.jglrxavpok.hephaistos.json.NBTGsonReader;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTType;
import org.jglrxavpok.hephaistos.parser.SNBTParser;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;

@UtilityClass
public final class PermissionUtil {
    public static final String PERMISSION_KEY = "permission";
    public static final String DATA_KEY = "data";
    private static final Gson GSON = new Gson();

    public static Permission toPermission(Map<String, Object> map) {
        String permission = Objects.toString(map.get(PERMISSION_KEY));
        if (permission == null) return null;

        NBTCompound data = null;
        if (map.containsKey(DATA_KEY)) {
            Object value = map.get(DATA_KEY);
            if (value instanceof String dataString) {
                try (Reader reader = new StringReader(dataString)) {
                    NBT nbt = new SNBTParser(reader).parse();
                    if (nbt instanceof NBTCompound compound) {
                        data = compound;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (value instanceof Map<?, ?> dataMap) {
                String json = GSON.toJson(dataMap);
                try (Reader reader = new StringReader(json)) {
                    NBT nbt = new NBTGsonReader(reader).read(NBTType.TAG_Compound);
                    if (nbt instanceof NBTCompound compound) {
                        data = compound;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return new Permission(permission, data);
    }

    public static List<Permission> toPermission(List<Map<String, Object>> mapList) {
        List<Permission> list = new ArrayList<>();
        for (Map<String, Object> map : mapList) {
            Permission permission = toPermission(map);
            if (permission == null) continue;
            list.add(permission);
        }
        return list;
    }

    public static Map<String, Object> toMap(Permission permission) {
        Map<String, Object> map = new HashMap<>();
        map.put(PERMISSION_KEY, permission.getPermissionName());
        if (permission.getNBTData() != null) {
            map.put(DATA_KEY, permission.getNBTData().toSNBT());
        }
        return map;
    }

    public static List<Map<String, Object>> toMap(List<Permission> permissionList) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Permission permission : permissionList) {
            list.add(toMap(permission));
        }
        return list;
    }
}
