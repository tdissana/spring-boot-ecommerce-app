package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.util.AuthUtil;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressServiceImpl implements AddressService{

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public AddressDTO addAddress(AddressDTO addressDTO) {

        User user = authUtil.loggedInUser();
        if (user == null) {
            throw new APIException("Currently no user logged in!");
        }

        Address address = modelMapper.map(addressDTO, Address.class);
        address.setUser(user);
        Address savedAddress = addressRepository.save(address);

        List<Address> addresses = user.getAddresses();
        addresses.add(savedAddress);
        user.setAddresses(addresses);

        AddressDTO savedAddressDTO = modelMapper.map(savedAddress, AddressDTO.class);
        return savedAddressDTO;
    }

    @Override
    public List<AddressDTO> getAllAddresses() {
        List<Address> addresses = addressRepository.findAll();
        List<AddressDTO> addressDTOS = addresses.stream()
                .map(address -> modelMapper.map(address, AddressDTO.class))
                .toList();

        return addressDTOS;
    }

    @Override
    public AddressDTO getAddressById(Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));
        AddressDTO addressDTO = modelMapper.map(address, AddressDTO.class);
        return addressDTO;
    }

    @Override
    public List<AddressDTO> getUserAddresses() {
        User user = authUtil.loggedInUser();
        if (user == null) {
            throw new APIException("No user logged in!");
        }

        List<Address> addresses = addressRepository.findAddressesByUserId(user.getUserId());

        List<AddressDTO> addressDTOS = addresses.stream()
                .map(address -> modelMapper.map(address, AddressDTO.class))
                .toList();
        return addressDTOS;
    }

    @Override
    public AddressDTO updateAddress(Long addressId, AddressDTO addressDTO) {

        User user = authUtil.loggedInUser();
        if (user == null) {
            throw new APIException("No user logged in!");
        }
        Address address = addressRepository.findByIdAndUserId(addressId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        address.setStreet(addressDTO.getStreet());
        address.setBuildingName(addressDTO.getBuildingName());
        address.setCity(addressDTO.getCity());
        address.setState(addressDTO.getState());
        address.setCountry(addressDTO.getCountry());
        address.setPincode(addressDTO.getPincode());

        Address updatedAddress = addressRepository.save(address);

        user.getAddresses().removeIf(address1 -> address1.getAddressId().equals(addressId));
        user.getAddresses().add(updatedAddress);

        AddressDTO updatedAddressDTO = modelMapper.map(address, AddressDTO.class);
        return updatedAddressDTO;
    }

    @Override
    public String deleteAddress(Long addressId) {
        User user = authUtil.loggedInUser();
        if (user == null) {
            throw new APIException("No user logged in!");
        }
        Address address = addressRepository.findByIdAndUserId(addressId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        addressRepository.delete(address);

        user.getAddresses().removeIf(address1 -> address1.getAddressId().equals(addressId));

        return "Address deleted successfully!";
    }
}
