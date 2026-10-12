package com.example.crud;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
@Entity @Table(name="customers")
public class Customer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Size(max=100) private String name;
 @NotBlank @Email @Size(max=180) @Column(unique=true) private String email;
 public Customer() {} public Customer(String name,String email){this.name=name;this.email=email;}
 public Long getId(){return id;} public String getName(){return name;} public void setName(String n){name=n;}
 public String getEmail(){return email;} public void setEmail(String e){email=e;}
}