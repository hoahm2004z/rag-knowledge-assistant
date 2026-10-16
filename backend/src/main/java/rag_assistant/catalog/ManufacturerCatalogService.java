package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.Manufacturer;
import rag_assistant.repository.ManufacturerRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManufacturerCatalogService {

    private final ManufacturerRepository manufacturerRepository;

    public List<Manufacturer> getAllManufacturers() {
        return manufacturerRepository.findAll();
    }
}
