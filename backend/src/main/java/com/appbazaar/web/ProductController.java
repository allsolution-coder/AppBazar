package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.storage.FileStorage;
import com.appbazaar.web.Dto.*;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private static final Set<String> CATEGORIES =
            Set.of("Software", "Apps", "Website templates", "AI tools", "Services",
                    "College projects", "Mini projects");

    private final ProductRepository products;
    private final UserRepository users;
    private final FileStorage storage;

    public ProductController(ProductRepository products, UserRepository users, FileStorage storage) {
        this.products = products; this.users = users; this.storage = storage;
    }

    @GetMapping
    public List<ProductRes> list(@RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "All") String category) {
        return products.search(q, category).stream().map(ProductRes::of).toList();
    }

    @GetMapping("/{id:\\d+}")
    public ProductRes one(@PathVariable Long id) {
        return products.findById(id).filter(p -> p.getStatus() == Status.LIVE).map(ProductRes::of)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
    }

    @GetMapping("/mine")
    public List<ProductRes> mine(Authentication auth) {
        User seller = users.findByEmail(auth.getName()).orElseThrow();
        return products.findBySellerOrderByIdDesc(seller).stream().map(ProductRes::of).toList();
    }

    /** Multipart upload: product details plus the file buyers will download. Goes to the approval queue. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductRes create(@RequestParam String name, @RequestParam String category,
                             @RequestParam int price, @RequestParam String description,
                             @RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) String techStack,
                             @RequestParam(required = false) String college, Authentication auth) {
        if (name.isBlank() || description.isBlank())
            throw new ResponseStatusException(BAD_REQUEST, "Name and description are required");
        if (price < 1) throw new ResponseStatusException(BAD_REQUEST, "Price must be at least 1");
        if (!CATEGORIES.contains(category)) throw new ResponseStatusException(BAD_REQUEST, "Unknown category");
        boolean project = category.endsWith(" projects");
        if (project && (techStack == null || techStack.isBlank()))
            throw new ResponseStatusException(BAD_REQUEST, "Please add the tech stack of your project");
        if ((techStack != null && techStack.length() > 200) || (college != null && college.length() > 200))
            throw new ResponseStatusException(BAD_REQUEST, "Tech stack and college name must be under 200 characters");
        FileStorage.Saved saved = storage.save(file);
        Product p = new Product();
        p.setName(name.trim());
        p.setCategory(category);
        p.setPrice(price);
        p.setDescription(description.trim());
        p.setStatus(Status.PENDING); // goes live only after admin approval
        p.setFileName(saved.originalName());
        p.setStoredFileName(saved.storedName());
        p.setFileSize(saved.size());
        p.setTechStack(techStack == null || techStack.isBlank() ? null : techStack.trim());
        p.setCollege(college == null || college.isBlank() ? null : college.trim());
        p.setSeller(users.findByEmail(auth.getName()).orElseThrow());
        return ProductRes.of(products.save(p));
    }
}
