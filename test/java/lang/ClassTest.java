package java.lang;


import java.lang.reflect.Field;

class ClassTest {

    public static void main(String[] args) {
        Field[] declaredFields = HelloServiceImp.class.getDeclaredFields();
        System.out.println(declaredFields);
    }




    static interface  HelloService{
        String name="zhangsan";
    }

    static class HelloServiceImp  implements HelloService{
        private  int age;

    }

}