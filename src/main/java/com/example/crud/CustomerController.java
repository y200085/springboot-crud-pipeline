package com.example.crud;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Controller
public class CustomerController {
 private final CustomerRepository repo;
 public CustomerController(CustomerRepository repo){this.repo=repo;}
 @GetMapping({"/","/customers"}) public String list(Model model){model.addAttribute("customers",repo.findAll());return "customers";}
 @GetMapping("/customers/new") public String createForm(Model model){model.addAttribute("customer",new Customer());return "customer-form";}
 @PostMapping("/customers") public String create(@Valid @ModelAttribute Customer customer, BindingResult errors){if(errors.hasErrors())return "customer-form";repo.save(customer);return "redirect:/customers";}
 @GetMapping("/customers/{id}/edit") public String editForm(@PathVariable Long id,Model model){model.addAttribute("customer",find(id));return "customer-form";}
 @PostMapping("/customers/{id}") public String update(@PathVariable Long id,@Valid @ModelAttribute Customer customer,BindingResult errors){if(errors.hasErrors())return "customer-form";Customer existing=find(id);existing.setName(customer.getName());existing.setEmail(customer.getEmail());repo.save(existing);return "redirect:/customers";}
 @PostMapping("/customers/{id}/delete") public String delete(@PathVariable Long id){repo.delete(find(id));return "redirect:/customers";}
 private Customer find(Long id){return repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
}