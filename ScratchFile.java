public class ScratchFile {

    public static void main(String[] args) {

String regexStr =  "api/v1/*";



        String regexString = "(?!popcorn).*$";
        System.out.println("[popcorn] " + ("popcorn".matches(regexString) ? "matched!" : "nope!"));
        System.out.println("[unicorn] " + ("unicorn".matches(regexString) ? "matched!" : "nope!"));
    }
}
