package com.appbazaar.web;

import static org.springframework.http.HttpStatus.*;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import com.appbazaar.storage.FileStorage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final FileStorage storage;

    public FileController(ProductRepository products, OrderRepository orders, UserRepository users, FileStorage storage) {
        this.products = products; this.orders = orders; this.users = users; this.storage = storage;
    }

    /** Only a buyer who paid for the product, its seller, or an admin can download the file. */
    @GetMapping("/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(@PathVariable Long productId, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        Product p = products.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        if (p.getStoredFileName() == null)
            throw new ResponseStatusException(NOT_FOUND, "This listing has no file attached");
        boolean allowed = u.getRole() == Role.ADMIN
                || p.getSeller().getId().equals(u.getId())
                || orders.hasPurchased(u, productId);
        if (!allowed) throw new ResponseStatusException(FORBIDDEN, "Buy this product to download its file");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(p.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(storage.load(p.getStoredFileName()));
    }
}
