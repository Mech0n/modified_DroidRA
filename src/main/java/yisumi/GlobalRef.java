package yisumi;

import java.util.Map;
import java.util.Set;

import yisumi.model.StmtKey;
import yisumi.model.StmtValue;
import yisumi.model.UniqStmt;
import yisumi.model.ReflectionProfile.RClass;
import yisumi.typeref.ArrayVarValue;

public class GlobalRef 
{
	public static String apkPath;
	public static String pkgName;
	public static String apkVersionName;
	public static int apkVersionCode = -1;
	public static int apkMinSdkVersion;
	public static Set<String> apkPermissions;
	
	public static String clsPath;
	public static int missingUnit = 0;
	
	//Configuration files
	public static final String WORKSPACE = "workspace";
	public static String fieldCallsConfigPath = "res/FieldCalls.txt";
	//public static String coalModelPath = "res/reflection.model";
	public static String coalModelPath = "res/reflection_simple.model";
	public static String rfModelPath = "res/reflection.model";
	public static String dclModelPath = "res/dynamic_code_loading.model";
	
	public static Map<UniqStmt, StmtValue> uniqStmtKeyValues;
	public static Map<String, RClass> rClasses;
	public static Map<UniqStmt, ArrayVarValue[]> arrayTypeRef;
	public static Map<UniqStmt, StmtKey> keyPairs;
	
	
	public static final String jsonFile = "refl.json";
    public static String android_jar;
}
