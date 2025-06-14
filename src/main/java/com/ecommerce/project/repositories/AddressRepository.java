package com.ecommerce.project.repositories;

import com.ecommerce.project.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    @Query("SELECT a FROM Address a WHERE a.user.id = ?1")
    List<Address> findAddressesByUserId(Long userId);

    @Query("SELECT a FROM Address a WHERE a.id = ?1 AND a.user.id = ?2")
    Optional<Address> findByIdAndUserId(Long addressId, Long userId);
}
